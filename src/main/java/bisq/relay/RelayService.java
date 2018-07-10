/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.relay;

import java.net.URL;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.util.concurrent.ExecutionException;

import lombok.extern.slf4j.Slf4j;



import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.turo.pushy.apns.ApnsClient;
import com.turo.pushy.apns.ApnsClientBuilder;
import com.turo.pushy.apns.PushNotificationResponse;
import com.turo.pushy.apns.util.ApnsPayloadBuilder;
import com.turo.pushy.apns.util.SimpleApnsPushNotification;
import com.turo.pushy.apns.util.concurrent.PushNotificationFuture;

@Slf4j
public class RelayService {
    private static final String IOS_BUNDLE_IDENTIFIER = "com.joachimneumann.bisqremotetest";
    private static final String IOS_CERTIFICATE_FILE = "push_certificate.production.p12";
    private static final String ANDROID_CERTIFICATE_FILE = "serviceAccountKey.json";
    private static final String ANDROID_DATABASE_URL = "https://bisqremotetest.firebaseio.com";

    private ApnsClient apnsClient;

    private boolean isProductionMode;

    public RelayService(boolean isProductionMode) {
        this.isProductionMode = isProductionMode;
        try {
            setup();
        } catch (IOException e) {
            log.error(e.toString());
            e.printStackTrace();
        }
    }

    String sendMessage(boolean isAndroid, String token, String encryptedMessage, boolean useSound) {
        if (isAndroid) {
            return sendAndroidMessage(token, encryptedMessage, useSound);
        } else {
            return sendAppleMessage(token, encryptedMessage, useSound);
        }
    }

    private void setup() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();

        InputStream androidCert = classLoader.getResourceAsStream(ANDROID_CERTIFICATE_FILE);
        if (androidCert == null) {
            throw new IOException(ANDROID_CERTIFICATE_FILE + " does not exist");
        } else {
            FirebaseOptions options = new FirebaseOptions.Builder()
                .setCredentials(GoogleCredentials.fromStream(androidCert))
                .setDatabaseUrl(ANDROID_DATABASE_URL)
                .build();

            FirebaseApp.initializeApp(options);
        }

        URL iosCert = classLoader.getResource(IOS_CERTIFICATE_FILE);
        if (iosCert == null) {
            throw new IOException(IOS_CERTIFICATE_FILE + " does not exist");
        } else {
            File p12File = new File(iosCert.getFile());
            log.info("Using iOS certification file {}.", p12File.getAbsolutePath());
            String apnsServer = isProductionMode ? ApnsClientBuilder.PRODUCTION_APNS_HOST : ApnsClientBuilder.DEVELOPMENT_APNS_HOST;
            apnsClient = new ApnsClientBuilder()
                .setApnsServer(apnsServer)
                .setClientCredentials(p12File, "")
                .build();
        }
    }

    private String sendAppleMessage(String apsTokenHex, String encryptedMessage, boolean useSound) {
        ApnsPayloadBuilder payloadBuilder = new ApnsPayloadBuilder();
        if (useSound)
            payloadBuilder.setSoundFileName("default");
        payloadBuilder.setAlertBody("Bisq notifcation");
        payloadBuilder.addCustomProperty("encrypted", encryptedMessage);
        final String payload = payloadBuilder.buildWithDefaultMaximumLength();
        SimpleApnsPushNotification simpleApnsPushNotification = new SimpleApnsPushNotification(apsTokenHex, IOS_BUNDLE_IDENTIFIER, payload);

        PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>>
            notificationFuture = apnsClient.sendNotification(simpleApnsPushNotification);
        try {
            PushNotificationResponse<SimpleApnsPushNotification> pushNotificationResponse = notificationFuture.get();
            if (pushNotificationResponse.isAccepted()) {
                log.info("Push notification accepted by APNs gateway.");
                return "success";
            } else {
                String msg1 = "Notification rejected by the APNs gateway: " +
                    pushNotificationResponse.getRejectionReason();
                String msg2 = "";
                if (pushNotificationResponse.getTokenInvalidationTimestamp() != null)
                    msg2 = " and the token is invalid as of " +
                        pushNotificationResponse.getTokenInvalidationTimestamp();

                log.info(msg1 + msg2);
                return "Error: " + msg1 + msg2;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error(e.toString());
            e.printStackTrace();
            return "Error: " + e.toString();
        }
    }

    private String sendAndroidMessage(String apsTokenHex, String encryptedMessage, boolean useSound) {
        Message.Builder builder = Message.builder()
            .putData("encrypted", encryptedMessage)
            .setToken(apsTokenHex);
        if (useSound)
            builder.putData("sound", "default");
        Message message = builder.build();
        try {
            FirebaseMessaging firebaseMessaging = FirebaseMessaging.getInstance();
            return firebaseMessaging.send(message);
        } catch (FirebaseMessagingException e) {
            log.error(e.toString());
            e.printStackTrace();
            return "Error: " + e.toString();
        }
    }
}
