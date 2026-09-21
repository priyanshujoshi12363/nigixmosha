package com.nigixmosha.app.data;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureBox {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String alias;
    private SecretKey key;

    public SecureBox(String alias) {
        this.alias = alias;
    }

    public synchronized String seal(String plain) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        ByteBuffer out = ByteBuffer.allocate(iv.length + sealed.length);
        out.put(iv).put(sealed);
        return Base64.encodeToString(out.array(), Base64.NO_WRAP);
    }

    public synchronized String open(String stored) throws Exception {
        byte[] all = Base64.decode(stored, Base64.NO_WRAP);
        if (all.length <= IV_BYTES) throw new IllegalStateException("corrupt value");
        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
        return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
    }

    private SecretKey key() throws Exception {
        if (key != null) return key;
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        KeyStore.Entry entry = store.getEntry(alias, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            key = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            return key;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        key = generator.generateKey();
        return key;
    }
}
