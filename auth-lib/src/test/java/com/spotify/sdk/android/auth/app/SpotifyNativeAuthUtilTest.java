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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SpotifyNativeAuthUtilTest {

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
    public void shouldReturnTrueWhenInstalledWithCorrectSignature() {
        Context mockedContext = mock(Context.class);
        Sha1HashUtil mockedHashUtil = mock(Sha1HashUtil.class);

        configureMocks(mockedContext, mockedHashUtil, SpotifyNativeAuthUtil.SPOTIFY_SIGNATURE_HASH[0]);
        boolean isInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(
                mockedContext,
                mockedHashUtil
        );

        assertTrue(isInstalled);
    }

    @Test
    public void shouldReturnFalseWhenInstalledWithIncorrectSignature() {
        Context mockedContext = mock(Context.class);
        Sha1HashUtil mockedHashUtil = mock(Sha1HashUtil.class);

        configureMocks(mockedContext, mockedHashUtil, "anothervalue");
        boolean isInstalled = SpotifyNativeAuthUtil.isSpotifyInstalled(
                mockedContext,
                mockedHashUtil
        );

        assertFalse(isInstalled);
    }

    private void configureMocks(Context mockedContext,
                                Sha1HashUtil mockedHashUtil,
                                String sha1result) {
        PackageManager mockedPackageManager = mock(PackageManager.class);
        when(mockedContext.getPackageManager()).thenReturn(mockedPackageManager);
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        PackageInfo packageInfo = new PackageInfo();
        Signature mockedSignature = mock(Signature.class);
        when(mockedSignature.toCharsString()).thenReturn("signature");
        packageInfo.signatures = new Signature[]{mockedSignature};
        String packageName = "package";
        info.activityInfo.applicationInfo = new ApplicationInfo();
        info.activityInfo.applicationInfo.packageName = packageName;
        info.activityInfo.name = "";
        when(mockedHashUtil.sha1Hash(any())).thenReturn(sha1result);
        when(mockedPackageManager.resolveActivity(any(), anyInt())).thenReturn(info);
        try {
            when(mockedPackageManager.getPackageInfo(eq(packageName), anyInt())).thenReturn(packageInfo);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }
}
