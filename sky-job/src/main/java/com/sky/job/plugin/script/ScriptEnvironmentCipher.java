package com.sky.job.plugin.script;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ScriptEnvironmentCipher {
    private final String configuredKey;
    private final SecureRandom random=new SecureRandom();
    public ScriptEnvironmentCipher(@Value("${sky.plugin.script-encryption-key:}") String key){configuredKey=key;}
    private SecretKeySpec key() {
        try {
            byte[] bytes=Base64.getDecoder().decode(configuredKey);
            if(bytes.length!=32)throw new IllegalArgumentException();
            return new SecretKeySpec(bytes,"AES");
        } catch(Exception e){throw new IllegalStateException("Configure SKY_SCRIPT_ENV_ENCRYPTION_KEY as a Base64 encoded 32-byte key");}
    }
    public String encrypt(String value,String identity) {
        var key=key();
        try {
            byte[] nonce=new byte[12];random.nextBytes(nonce);
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
            cipher.updateAAD(identity.getBytes(StandardCharsets.UTF_8));
            return "v1:"+Base64.getEncoder().encodeToString(nonce)+":"+Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch(Exception e){throw new IllegalStateException("Cannot encrypt script environment");}
    }
    public String decrypt(String value,String identity) {
        var key=key();
        try {
            String[] parts=value.split(":",-1);
            if(parts.length!=3 || !parts[0].equals("v1"))throw new IllegalArgumentException();
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.getDecoder().decode(parts[1])));
            cipher.updateAAD(identity.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])),StandardCharsets.UTF_8);
        } catch(Exception e){throw new IllegalStateException("Cannot decrypt script environment; verify SKY_SCRIPT_ENV_ENCRYPTION_KEY");}
    }
}
