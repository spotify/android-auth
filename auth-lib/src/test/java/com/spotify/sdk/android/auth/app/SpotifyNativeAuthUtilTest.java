package com.spotify.sdk.android.auth.app;

import static com.spotify.sdk.android.auth.AuthorizationRequest.ANDROID_SDK;
import static com.spotify.sdk.android.auth.AuthorizationRequest.SPOTIFY_SDK;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;
import com.spotify.sdk.android.auth.IntentExtras;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class SpotifyNativeAuthUtilTest {

    private static final String SPOTIFY_HASH = "25a9b2d2745c098361edaa3b87936dc29a28e7f1";
    private static final String DEFAULT_TEST_SIGNATURE = "signature";
    private Sha1HashUtil mSha1HashUtil;

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
        mSha1HashUtil = new FakeSha1HashUtil(Collections.singletonMap(DEFAULT_TEST_SIGNATURE, SPOTIFY_HASH));
        configureDefaultMocks(mockedContext);
        boolean isInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(
                mockedContext,
                mSha1HashUtil
        );

        assertTrue(isInstalled);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void shouldReturnTrueWhenInstalledWithCorrectSignatureAboveAPIP() {
        Context mockedContext = mock(Context.class);
        mSha1HashUtil = new FakeSha1HashUtil(Collections.singletonMap(DEFAULT_TEST_SIGNATURE, SPOTIFY_HASH));
        configureMocksWithSigningInfo(mockedContext);
        boolean isInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(
                mockedContext,
                mSha1HashUtil
        );

        assertTrue(isInstalled);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void shouldReturnTrueWhenInstalledWithMultipleCorrectSignatureAboveAPIP() {
        Context mockedContext = mock(Context.class);
        mSha1HashUtil = new FakeSha1HashUtil(Collections.singletonMap(DEFAULT_TEST_SIGNATURE, SPOTIFY_HASH));
        PackageInfo packageInfo = new PackageInfo();
        Signature mockedSignature = mock(Signature.class);
        when(mockedSignature.toCharsString()).thenReturn(DEFAULT_TEST_SIGNATURE);
        SigningInfo mockedSigningInfo = mock(SigningInfo.class);
        when(mockedSigningInfo.hasMultipleSigners()).thenReturn(true);
        when(mockedSigningInfo.getApkContentsSigners()).thenReturn(new Signature[]{mockedSignature, mockedSignature});
        packageInfo.signingInfo = mockedSigningInfo;
        configureMocks(mockedContext, packageInfo);
        boolean isInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(
                mockedContext,
                mSha1HashUtil
        );

        assertTrue(isInstalled);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void shouldReturnFalseWhenInstalledWithOneIncorrectSignatureAboveAPIP() {
        Context mockedContext = mock(Context.class);
        mSha1HashUtil = new FakeSha1HashUtil(Collections.singletonMap(DEFAULT_TEST_SIGNATURE, SPOTIFY_HASH));
        PackageInfo packageInfo = new PackageInfo();
        Signature mockedSignature = mock(Signature.class);
        when(mockedSignature.toCharsString()).thenReturn(DEFAULT_TEST_SIGNATURE);
        Signature mockedOtherSignature = mock(Signature.class);
        when(mockedOtherSignature.toCharsString()).thenReturn("other_signature");
        SigningInfo mockedSigningInfo = mock(SigningInfo.class);
        when(mockedSigningInfo.hasMultipleSigners()).thenReturn(true);
        when(mockedSigningInfo.getApkContentsSigners()).thenReturn(new Signature[]{mockedSignature, mockedOtherSignature});
        packageInfo.signingInfo = mockedSigningInfo;
        configureMocks(mockedContext, packageInfo);
        boolean isInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(
                mockedContext,
                mSha1HashUtil
        );

        assertFalse(isInstalled);
    }

    @Test
    public void shouldReturnFalseWhenInstalledWithIncorrectSignature() {
        Context mockedContext = mock(Context.class);
        mSha1HashUtil = new FakeSha1HashUtil(Collections.singletonMap(DEFAULT_TEST_SIGNATURE, "anothervalue"));
        configureDefaultMocks(mockedContext);
        boolean isInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(
                mockedContext,
                mSha1HashUtil
        );

        assertFalse(isInstalled);
    }

    @Test
    public void hasUtmExtrasSetToLoginIntent() {
        String campaign = "campaign";
        Activity activity = mock(Activity.class);
        Mockito.doNothing().when(activity).startActivityForResult(any(Intent.class), anyInt());
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest
                        .Builder("test", AuthorizationResponse.Type.TOKEN, "to://me")
                        .setScopes(new String[]{"testa", "toppen"})
                        .setCampaign(campaign)
                        .build();
        configureMocksWithSigningInfo(activity);
        SpotifyNativeAuthUtil authUtil = new SpotifyNativeAuthUtil(
                activity,
                authorizationRequest,
                new FakeSha1HashUtil(Collections.singletonMap(DEFAULT_TEST_SIGNATURE, SPOTIFY_HASH))
        );
        authUtil.startAuthActivity();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(activity, times(1)).startActivityForResult(captor.capture(), anyInt());
        Intent intent = captor.getValue();

        assertEquals(SPOTIFY_SDK, intent.getStringExtra(IntentExtras.KEY_UTM_SOURCE));
        assertEquals(ANDROID_SDK, intent.getStringExtra(IntentExtras.KEY_UTM_MEDIUM));
        assertEquals(campaign, intent.getStringExtra(IntentExtras.KEY_UTM_CAMPAIGN));
    }

    private void configureDefaultMocks(Context mockedContext) {
        PackageInfo packageInfo = new PackageInfo();
        Signature mockedSignature = mock(Signature.class);
        when(mockedSignature.toCharsString()).thenReturn(DEFAULT_TEST_SIGNATURE);
        packageInfo.signatures = new Signature[]{mockedSignature};
        configureMocks(mockedContext, packageInfo);
    }

    private void configureMocksWithSigningInfo(Context mockedContext) {
        PackageInfo packageInfo = new PackageInfo();
        Signature mockedSignature = mock(Signature.class);
        when(mockedSignature.toCharsString()).thenReturn(DEFAULT_TEST_SIGNATURE);
        SigningInfo mockedSigningInfo = mock(SigningInfo.class);
        when(mockedSigningInfo.hasMultipleSigners()).thenReturn(false);
        when(mockedSigningInfo.getSigningCertificateHistory()).thenReturn(new Signature[]{mockedSignature});
        packageInfo.signingInfo = mockedSigningInfo;
        configureMocks(mockedContext, packageInfo);
    }

    private void configureMocks(Context mockedContext, PackageInfo packageInfo) {
        PackageManager mockedPackageManager = mock(PackageManager.class);
        when(mockedContext.getPackageManager()).thenReturn(mockedPackageManager);
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        String packageName = "package";
        info.activityInfo.applicationInfo = new ApplicationInfo();
        info.activityInfo.applicationInfo.packageName = packageName;
        info.activityInfo.name = "";
        when(mockedPackageManager.resolveActivity(any(), anyInt())).thenReturn(info);
        try {
            when(mockedPackageManager.getPackageInfo(eq(packageName), anyInt())).thenReturn(packageInfo);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }
}
