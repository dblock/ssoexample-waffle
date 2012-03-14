package ssoexample;

import java.io.Serializable;

public class TokenMessage implements Serializable {
    private byte[] token;

    public TokenMessage(byte[] token) {
        this.token = token;
    }

    public byte[] getToken() {
        return token;
    }

    @Override
    public String toString() {
        return super.toString() + ": " + byteArrayToHexString(token);
    }

    public static String byteArrayToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            int v = b & 0xff;
            if (v < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
            sb.append(" ");
        }
        return sb.toString().toUpperCase();
    }
}
