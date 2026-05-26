package com.hrms;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class GenHash {
    public static void main(String[] args) {
        var enc = new BCryptPasswordEncoder(12);
        String hash = enc.encode("Admin@123456");
        System.out.println("HASH=" + hash);
        System.out.println("OLD_MATCH=" + enc.matches("Admin@123456", "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewylxXqtBSi7yQhy"));
    }
}
