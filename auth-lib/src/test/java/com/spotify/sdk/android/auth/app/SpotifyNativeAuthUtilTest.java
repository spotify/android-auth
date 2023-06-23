package com.spotify.sdk.android.auth.app;

import static junit.framework.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
public class SpotifyNativeAuthUtilTest {

    private static final String SPOTIFY_HASH = "25a9b2d2745c098361edaa3b87936dc29a28e7f1";
    private static final String DEFAULT_TEST_SIGNATURE = "signature";

    @Test
    public void shouldReturnFalseWhenNotInstalled() {
        Context mockedContext = mock(Context.class);
        PackageManager mockedPackageManager = mock(PackageManager.class);
        when(mockedContext.getPackageManager()).thenReturn(mockedPackageManager);
        boolean isInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(
                mockedContext
        );

        assertFalse(isInstalled);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.O)
    public void shouldReturnTrueWhenInstalledWithCorrectSignatureBelowAPIP() {
        Context mockedContext = mock(Context.class);
        Sha1HashUtil mockedHashUtil = mock(Sha1HashUtil.class);

        configureDefaultMocks(mockedContext, mockedHashUtil, SPOTIFY_HASH);
        boolean isInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(
                mockedContext,
                mockedHashUtil
        );

        assertTrue(isInstalled);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void shouldReturnTrueWhenInstalledWithCorrectSignatureAboveAPIP() {
        Context mockedContext = mock(Context.class);
        Sha1HashUtil mockedHashUtil = mock(Sha1HashUtil.class);

        PackageInfo packageInfo = new PackageInfo();
        Signature mockedSignature = mock(Signature.class);
        when(mockedSignature.toCharsString()).thenReturn(DEFAULT_TEST_SIGNATURE);
        SigningInfo mockedSigningInfo = mock(SigningInfo.class);
        when(mockedSigningInfo.hasMultipleSigners()).thenReturn(false);
        when(mockedSigningInfo.getSigningCertificateHistory()).thenReturn(new Signature[]{mockedSignature});
        packageInfo.signingInfo = mockedSigningInfo;
        configureMocks(mockedContext,
                mockedHashUtil,
                SPOTIFY_HASH,
                packageInfo);
        boolean isInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(
                mockedContext,
                mockedHashUtil
        );

        assertTrue(isInstalled);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void shouldReturnTrueWhenInstalledWithMultipleCorrectSignatureAboveAPIP() {
        Context mockedContext = mock(Context.class);
        Sha1HashUtil mockedHashUtil = mock(Sha1HashUtil.class);

        PackageInfo packageInfo = new PackageInfo();
        Signature mockedSignature = mock(Signature.class);
        when(mockedSignature.toCharsString()).thenReturn(DEFAULT_TEST_SIGNATURE);
        SigningInfo mockedSigningInfo = mock(SigningInfo.class);
        when(mockedSigningInfo.hasMultipleSigners()).thenReturn(true);
        when(mockedSigningInfo.getApkContentsSigners()).thenReturn(new Signature[]{mockedSignature, mockedSignature});
        packageInfo.signingInfo = mockedSigningInfo;
        configureMocks(mockedContext, mockedHashUtil, SPOTIFY_HASH, packageInfo);
        boolean isInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(
                mockedContext,
                mockedHashUtil
        );

        assertTrue(isInstalled);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void shouldReturnFalseWhenInstalledWithOneIncorrectSignatureAboveAPIP() {
        Context mockedContext = mock(Context.class);
        Sha1HashUtil mockedHashUtil = mock(Sha1HashUtil.class);

        PackageInfo packageInfo = new PackageInfo();
        Signature mockedSignature = mock(Signature.class);
        when(mockedSignature.toCharsString()).thenReturn(DEFAULT_TEST_SIGNATURE);
        Signature mockedOtherSignature = mock(Signature.class);
        when(mockedOtherSignature.toCharsString()).thenReturn("other_signature");
        SigningInfo mockedSigningInfo = mock(SigningInfo.class);
        when(mockedSigningInfo.hasMultipleSigners()).thenReturn(true);
        when(mockedSigningInfo.getApkContentsSigners()).thenReturn(new Signature[]{mockedSignature, mockedOtherSignature});
        packageInfo.signingInfo = mockedSigningInfo;
        configureMocks(mockedContext, mockedHashUtil, SPOTIFY_HASH, packageInfo);
        boolean isInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(
                mockedContext,
                mockedHashUtil
        );

        assertFalse(isInstalled);
    }

    @Test
    public void shouldReturnFalseWhenInstalledWithIncorrectSignature() {
        Context mockedContext = mock(Context.class);
        Sha1HashUtil mockedHashUtil = mock(Sha1HashUtil.class);

        configureDefaultMocks(mockedContext, mockedHashUtil, "anothervalue");
        boolean isInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(
                mockedContext,
                mockedHashUtil
        );

        assertFalse(isInstalled);
    }

    private void configureDefaultMocks(Context mockedContext,
                                       Sha1HashUtil mockedHashUtil,
                                       String sha1result) {
        PackageInfo packageInfo = new PackageInfo();
        Signature mockedSignature = mock(Signature.class);
        when(mockedSignature.toCharsString()).thenReturn(DEFAULT_TEST_SIGNATURE);
        packageInfo.signatures = new Signature[]{mockedSignature};
        configureMocks(mockedContext, mockedHashUtil, sha1result, packageInfo);
    }

    private void configureMocks(Context mockedContext,
                                Sha1HashUtil mockedHashUtil,
                                String sha1result,
                                PackageInfo packageInfo) {
        PackageManager mockedPackageManager = mock(PackageManager.class);
        when(mockedContext.getPackageManager()).thenReturn(mockedPackageManager);
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        String packageName = "package";
        info.activityInfo.applicationInfo = new ApplicationInfo();
        info.activityInfo.applicationInfo.packageName = packageName;
        info.activityInfo.name = "";
        when(mockedHashUtil.sha1Hash(DEFAULT_TEST_SIGNATURE)).thenReturn(sha1result);
        when(mockedPackageManager.resolveActivity(any(), anyInt())).thenReturn(info);
        try {
            when(mockedPackageManager.getPackageInfo(eq(packageName), anyInt())).thenReturn(packageInfo);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }
}
