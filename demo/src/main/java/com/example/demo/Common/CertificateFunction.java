package com.example.demo.Common;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import javax.security.auth.x500.X500Principal;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.security.KeyStore;
//import java.security.KeyStoreException;
//import java.security.NoSuchAlgorithmException;
//import java.security.cert.Certificate;
//import java.security.cert.CertificateException;
//import java.security.cert.X509Certificate;
//import java.util.Enumeration;
//import java.util.Map;

public class CertificateFunction {

//    private static final Logger logger = LoggerFactory.getLogger(CertificateFunction.class);
//
//    private static final Map<String, String> keystore = Map.of(
//            "permission-keystorePath",
//            System.getenv().getOrDefault(
//                    "PERMISSION_KEYSTORE_PATH",
//                    "src/main/resources/certs/permission-keystore.p12"
//            ),
//
//            "user-keystorePath",
//            System.getenv().getOrDefault(
//                    "USER_KEYSTORE_PATH",
//                    "src/main/resources/certs/user-keystore.p12"
//            ),
//
//            "keystorePassword",
//            System.getenv().getOrDefault(
//                    "KEYSTORE_PASSWORD",
//                    "1qaz@WSX"
//            )
//    );

//    public static boolean certificateCheck(String key, String name) {
//
//        logger.info("permission-keystorePath: {}", keystore.get("permission-keystorePath"));
//        logger.info("user-keystorePath: {}", keystore.get("user-keystorePath"));
//        logger.info("keystorePassword: {}", keystore.get("keystorePassword"));
//
//        boolean valid = true;
//        try {
//            // 1. 設定 keystore 路徑與密碼
//            String keystorePath = keystore.get(key);
//            String keystorePassword = keystore.get("keystorePassword");
//
//            // 2. 載入 keystore
//            KeyStore keyStore = KeyStore.getInstance("PKCS12");
//            try (FileInputStream fis = new FileInputStream(keystorePath)) {
//                keyStore.load(fis, keystorePassword.toCharArray());
//            }
//
//            // 取得所有憑證
//            Enumeration<String> aliases = keyStore.aliases();
//            while (aliases.hasMoreElements()) {
//                String alias = aliases.nextElement();
//
//                // 取得憑證並轉型成 X509Certificate
//                Certificate cert = keyStore.getCertificate(alias);
//                if (cert instanceof X509Certificate x509Cert) {
//                    X500Principal subject = x509Cert.getSubjectX500Principal();
//                    X500Principal issuer = x509Cert.getIssuerX500Principal();
//
//                    logger.info("{} Alias: {}", name, alias);
//                    logger.info("{} Subject: {}", name, subject.getName());
//                    logger.info("{} Issuer: {}", name, issuer.getName());
//                    logger.info("{} Serial Number: {}", name, x509Cert.getSerialNumber());
//
//                    // 檢查有效日期
//                    try {
//                        // 若過期或還沒生效會拋例外
//                        x509Cert.checkValidity();
//                        logger.info("{} Certificate is currently valid.", name);
//                    } catch (Exception e) {
//                        logger.info("{} Certificate not valid: {}", name, e.getMessage());
//                        valid = false;
//                    }
//                }
//            }
//        } catch (CertificateException | KeyStoreException | IOException | NoSuchAlgorithmException e) {
//            logger.error("{} Exception: {}", name, e.getMessage());
//            valid = false;
//        }
//        return valid;
//    }

}
