package com.spotify.sdk.android.auth.app;

import android.util.Pair;

import java.util.Map;

public class FakeSha1HashUtil implements Sha1HashUtil {

    private final Map<String, String> mSignatureHashMap;

    public FakeSha1HashUtil(Map<String, String> signatureHashMap) {
        mSignatureHashMap = signatureHashMap;
    }

    @Override
    public String sha1Hash(String signature) {
        return mSignatureHashMap.get(signature);
    }
}
