package org.infobip.mobile.messaging.mobile.appinstance;

import android.content.Context;

import org.infobip.mobile.messaging.CustomUserDataValue;
import org.infobip.mobile.messaging.Installation;
import org.infobip.mobile.messaging.InstallationMapper;
import org.infobip.mobile.messaging.MobileMessagingCore;
import org.infobip.mobile.messaging.MobileMessagingProperty;
import org.infobip.mobile.messaging.SystemData;
import org.infobip.mobile.messaging.UserDataMapper;
import org.infobip.mobile.messaging.api.appinstance.AppInstance;
import org.infobip.mobile.messaging.api.appinstance.MobileApiAppInstance;
import org.infobip.mobile.messaging.logging.MobileMessagingLogger;
import org.infobip.mobile.messaging.mobile.MobileMessagingError;
import org.infobip.mobile.messaging.mobile.common.MRetryableTask;
import org.infobip.mobile.messaging.mobile.common.RetryPolicyProvider;
import org.infobip.mobile.messaging.platform.Broadcaster;
import org.infobip.mobile.messaging.platform.Platform;
import org.infobip.mobile.messaging.stats.MobileMessagingStats;
import org.infobip.mobile.messaging.stats.MobileMessagingStatsError;
import org.infobip.mobile.messaging.util.DeviceInformation;
import org.infobip.mobile.messaging.util.PreferenceHelper;
import org.infobip.mobile.messaging.util.SoftwareInformation;
import org.infobip.mobile.messaging.util.StringUtils;
import org.infobip.mobile.messaging.util.SystemInformation;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;


public class InstallationSynchronizer {

    private final Context context;
    private final MobileMessagingCore mobileMessagingCore;
    private final MobileMessagingStats stats;
    private final Executor executor;
    private final Broadcaster broadcaster;
    private final RetryPolicyProvider retryPolicyProvider;
    private final MobileApiAppInstance mobileApiAppInstance;

    private static class PushInstallation extends Installation {
        void setServiceType() {
            super.setPushServiceType(Platform.usedPushServiceType);
        }

        void setToken(String token) {
            super.setPushServiceToken(token);
        }
    }

    public InstallationSynchronizer(
            Context context,
            MobileMessagingCore mobileMessagingCore,
            MobileMessagingStats stats,
            Executor executor,
            Broadcaster broadcaster,
            RetryPolicyProvider retryPolicyProvider,
            MobileApiAppInstance mobileApiAppInstance) {

        this.context = context;
        this.mobileMessagingCore = mobileMessagingCore;
        this.stats = stats;
        this.executor = executor;
        this.broadcaster = broadcaster;
        this.retryPolicyProvider = retryPolicyProvider;
        this.mobileApiAppInstance = mobileApiAppInstance;
    }

    public void sync() {
        sync(null);
    }

    public void sync(InstallationActionListener actionListener) {
        PushInstallation installation = new PushInstallation();

        SystemData systemDataForReport = systemDataForReport();
        if (systemDataForReport != null) {
            installation = from(systemDataForReport);
        }

        boolean cloudTokenPresentAndUnreported = isCloudTokenPresentAndUnreported();
        if (cloudTokenPresentAndUnreported) {
            installation.setToken(mobileMessagingCore.getCloudToken());
        }

        if (mobileMessagingCore.isPushServiceTypeChanged()) {
            installation.setServiceType();
        }

        if (mobileMessagingCore.getUnreportedPrimarySetting() != null) {
            installation.setPrimary(mobileMessagingCore.getUnreportedPrimarySetting());
        }

        if (!mobileMessagingCore.isApplicationUserIdReported()) {
            installation.setApplicationUserId(mobileMessagingCore.getApplicationUserId());
        }

        if (installation.hasDataToReport()) {
            installation.setRegEnabled(mobileMessagingCore.isPushRegistrationEnabled());
        }

        if (mobileMessagingCore.isRegistrationUnavailable()) {
            if (cloudTokenPresentAndUnreported) createInstance(installation, actionListener);
        } else {
            if (installation.hasDataToReport()) patch(installation, actionListener);
        }
    }

    public void updateApplicationUserId(String applicationUserId, InstallationActionListener actionListener) {
        Installation installation = new Installation();
        installation.setApplicationUserId(applicationUserId);
        patch(installation, actionListener);
    }

    public void updateCustomAttributes(Map<String,CustomUserDataValue> customAtts, InstallationActionListener actionListener) {
        Installation installation = new Installation();
        installation.setCustomAttributes(customAtts);
        patch(installation, actionListener);
    }

    public void updatePushRegEnabledStatus(Boolean enabled, InstallationActionListener actionListener) {
        Installation installation = new Installation();
        installation.setRegEnabled(enabled);
        patch(installation, actionListener);
    }

    public void updatePrimaryStatus(Boolean primary, InstallationActionListener actionListener) {
        updatePrimaryStatus(null, primary, actionListener);
    }

    public void updatePrimaryStatus(String pushRegId, Boolean primary, InstallationActionListener actionListener) {
        Installation installation = new Installation(pushRegId);
        installation.setPrimary(primary);
        patch(installation, actionListener);
    }

    private void createInstance(final Installation installation, final InstallationActionListener actionListener) {
        new MRetryableTask<Void, AppInstance>() {

            @Override
            public AppInstance run(Void[] voids) {
                MobileMessagingLogger.v("CREATE INSTALLATION >>>", installation);
                setCloudTokenReported(true);
                return mobileApiAppInstance.createInstance(false, InstallationMapper.toBackend(installation));
            }

            @Override
            public void after(AppInstance appInstance) {
                MobileMessagingLogger.v("CREATE INSTALLATION <<<", appInstance);

                if (appInstance == null) {
                    setCloudTokenReported(false);
                    return;
                }

                Installation installation = InstallationMapper.fromBackend(appInstance);
                setPushRegistrationId(installation.getPushRegId());
                updateInstallationReported(installation, true);

                broadcaster.installationCreated(installation);

                if (actionListener != null) {
                    actionListener.onSuccess(installation);
                }
            }

            @Override
            public void error(Throwable error) {
                MobileMessagingLogger.v("CREATE INSTALLATION ERROR <<<", error);
                setCloudTokenReported(false);

                mobileMessagingCore.setLastHttpException(error);
                stats.reportError(MobileMessagingStatsError.REGISTRATION_SYNC_ERROR);
                broadcaster.error(MobileMessagingError.createFrom(error));

                if (actionListener != null) {
                    actionListener.onError(MobileMessagingError.createFrom(error));
                }
            }
        }

                .retryWith(retryPolicyProvider.DEFAULT())
                .execute(executor);
    }

    public void patch(final Installation installation, final InstallationActionListener actionListener) {
        String pushRegId = mobileMessagingCore.getPushRegistrationId();
        final boolean myDevice = isMyDevice(installation, pushRegId);
        if (!myDevice) {
            pushRegId = installation.getPushRegId();
        }

        final String pushRegIdToUpdate = pushRegId;
        new MRetryableTask<Void, Void>() {
            @Override
            public Void run(Void[] voids) {
                MobileMessagingLogger.v("UPDATE INSTALLATION >>>");
                mobileApiAppInstance.patchInstance(pushRegIdToUpdate, true, new HashMap<>(installation.getMap()));
                return null;
            }

            @Override
            public void after(Void aVoid) {
                MobileMessagingLogger.v("UPDATE INSTALLATION <<<");

                updateInstallationReported(installation, myDevice);

                broadcaster.installationUpdated(installation);

                if (actionListener != null) {
                    actionListener.onSuccess(installation);
                }
            }

            @Override
            public void error(Throwable error) {
                MobileMessagingLogger.v("UPDATE INSTALLATION ERROR <<<", error);
                setCloudTokenReported(false);

                mobileMessagingCore.setLastHttpException(error);
                stats.reportError(MobileMessagingStatsError.REGISTRATION_SYNC_ERROR);
                broadcaster.error(MobileMessagingError.createFrom(error));

                if (actionListener != null) {
                    actionListener.onError(MobileMessagingError.createFrom(error));
                }
            }
        }
                .retryWith(retryPolicyProvider.DEFAULT())
                .execute(executor);
    }

    private boolean isMyDevice(Installation installation, String myPushRegId) {
        return  (installation.getPushRegId() != null && myPushRegId.equals(installation.getPushRegId())) || installation.getPushRegId() == null;
    }

    private void updateInstallationReported(Installation installation, boolean myDevice) {
        if (!myDevice) {
            PreferenceHelper.remove(context, MobileMessagingProperty.IS_PRIMARY_UNREPORTED);
            mobileMessagingCore.savePrimarySetting(false);
            return;
        }

        PreferenceHelper.remove(context, MobileMessagingProperty.IS_PRIMARY_UNREPORTED);
        if (installation.getPrimary() != null) {
            mobileMessagingCore.savePrimarySetting(installation.getPrimary());
        }
        setPushRegistrationEnabled(installation.getRegEnabled());
        setCloudTokenReported(true);
        mobileMessagingCore.setApplicationUserIdReported(true);

        String unreportedCustomAttributes = mobileMessagingCore.getUnreportedCustomAttributes();
        if (unreportedCustomAttributes != null) {
            mobileMessagingCore.setUnreportedCustomAttributes(null);
            String reportedCustomAtts = mobileMessagingCore.getCustomAttributes();
            Map<String, CustomUserDataValue> customAttsMap = UserDataMapper.customAttsFrom(reportedCustomAtts);
            Map<String, CustomUserDataValue> unreportedCustomAttsMap = UserDataMapper.customAttsFrom(unreportedCustomAttributes);
            if (customAttsMap == null) {
                customAttsMap = new HashMap<>();
            }
            customAttsMap.putAll(unreportedCustomAttsMap);

            mobileMessagingCore.saveCustomAttributes(customAttsMap);
        }
        mobileMessagingCore.setSystemDataReported();
        mobileMessagingCore.setReportedPushServiceType();
    }

    public void fetchInstance(final InstallationActionListener actionListener) {
        if (mobileMessagingCore.isRegistrationUnavailable()) {
            return;
        }

        new MRetryableTask<Void, AppInstance>() {
            @Override
            public AppInstance run(Void[] voids) {
                MobileMessagingLogger.v("GET INSTALLATION >>>");
                return mobileApiAppInstance.getInstance(mobileMessagingCore.getPushRegistrationId());
            }

            @Override
            public void after(AppInstance instance) {
                Installation installation = InstallationMapper.fromBackend(instance);
                if (installation.getPrimary() != null) {
                    mobileMessagingCore.savePrimarySetting(installation.getPrimary());
                }
                mobileMessagingCore.saveCustomAttributes(installation.getCustomAttributes());

                if (actionListener != null) {
                    actionListener.onSuccess(installation);
                }
                MobileMessagingLogger.v("GET INSTALLATION <<<");
            }

            @Override
            public void error(Throwable error) {
                if (actionListener != null) {
                    actionListener.onError(MobileMessagingError.createFrom(error));
                }
                MobileMessagingLogger.v("GET INSTALLATION ERROR <<<", error);
            }
        }
                .retryWith(retryPolicyProvider.DEFAULT())
                .execute(executor);
    }

    private boolean isCloudTokenPresentAndUnreported() {
        return !isCloudTokenReported() && StringUtils.isNotBlank(mobileMessagingCore.getCloudToken());
    }

    private SystemData systemDataForReport() {
        boolean reportEnabled = PreferenceHelper.findBoolean(context, MobileMessagingProperty.REPORT_SYSTEM_INFO);

        SystemData data = new SystemData(SoftwareInformation.getSDKVersionWithPostfixForSystemData(context),
                reportEnabled ? SystemInformation.getAndroidSystemVersion() : "",
                reportEnabled ? DeviceInformation.getDeviceManufacturer() : "",
                reportEnabled ? DeviceInformation.getDeviceModel() : "",
                reportEnabled ? SoftwareInformation.getAppVersion(context) : "",
                mobileMessagingCore.isGeofencingActivated(),
                SoftwareInformation.areNotificationsEnabled(context),
                reportEnabled && DeviceInformation.isDeviceSecure(context),
                reportEnabled ? SystemInformation.getAndroidSystemLanguage() : "",
                reportEnabled ? SystemInformation.getAndroidDeviceName(context) : "");

        Integer hash = PreferenceHelper.findInt(context, MobileMessagingProperty.REPORTED_SYSTEM_DATA_HASH);
        if (hash != data.hashCode()) {
            PreferenceHelper.saveString(context, MobileMessagingProperty.UNREPORTED_SYSTEM_DATA, data.toString());
            return data;
        }

        return null;
    }

    private void setPushRegistrationEnabled(Boolean pushRegistrationEnabled) {
        if (pushRegistrationEnabled == null) {
            return;
        }

        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.PUSH_REGISTRATION_ENABLED, pushRegistrationEnabled);
    }

    private void setPushRegistrationId(String registrationId) {
        if (registrationId == null) {
            return;
        }

        PreferenceHelper.saveString(context, MobileMessagingProperty.INFOBIP_REGISTRATION_ID, registrationId);
    }

    public void setCloudTokenReported(boolean reported) {
        PreferenceHelper.saveBoolean(context, MobileMessagingProperty.CLOUD_TOKEN_REPORTED, reported);
    }

    public boolean isCloudTokenReported() {
        return PreferenceHelper.findBoolean(context, MobileMessagingProperty.CLOUD_TOKEN_REPORTED);
    }

    private PushInstallation from(SystemData data) {
        PushInstallation installation = new PushInstallation();
        installation.setSdkVersion(data.getSdkVersion());
        installation.setOsVersion(data.getOsVersion());
        installation.setDeviceManufacturer(data.getDeviceManufacturer());
        installation.setDeviceModel(data.getDeviceModel());
        installation.setAppVersion(data.getApplicationVersion());
        installation.setGeoEnabled(data.isGeofencing());
        installation.setNotificationsEnabled(data.areNotificationsEnabled());
        installation.setDeviceSecure(data.isDeviceSecure());
        installation.setOsLanguage(data.getOsLanguage());
        installation.setDeviceName(data.getDeviceName());
        installation.setOs(Platform.os);
        return installation;
    }
}
