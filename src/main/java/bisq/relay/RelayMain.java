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

import bisq.common.app.Log;
import bisq.common.app.Version;
import bisq.common.util.Utilities;

import org.apache.commons.codec.binary.Hex;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import java.io.File;
import java.io.IOException;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

import static spark.Spark.get;
import static spark.Spark.port;

public class RelayMain {
    private static final Logger log = LoggerFactory.getLogger(RelayMain.class);
    public static final String VERSION = "0.6.4";
    private static RelayService relayService;

    static {
        // Need to set default locale initially otherwise we get problems at non-english OS
        Locale.setDefault(new Locale("en", Locale.getDefault().getCountry()));

        Utilities.removeCryptographyRestrictions();
    }

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        final String logPath = System.getProperty("user.home") + File.separator + "provider";
        Log.setup(logPath);
        Log.setLevel(Level.INFO);
        log.info("Log files under: " + logPath);
        log.info("RelayVersion.VERSION: " + VERSION);
        log.info("Bisq exchange Version{" +
            "VERSION=" + Version.VERSION +
            ", P2P_NETWORK_VERSION=" + Version.P2P_NETWORK_VERSION +
            ", LOCAL_DB_VERSION=" + Version.LOCAL_DB_VERSION +
            ", TRADE_PROTOCOL_VERSION=" + Version.TRADE_PROTOCOL_VERSION +
            ", BASE_CURRENCY_NETWORK=NOT SET" +
            ", getP2PNetworkId()=NOT SET" +
            '}');
        Utilities.printSysInfo();

        port(8888);

        relayService = new RelayService(true);

        handleRelay();

        keepRunning();
    }

    private static void handleRelay() throws IOException {
        get("/relay", (request, response) -> {
            log.info("Incoming relay request from: " + request.userAgent());
            // http://localhost:8080/hello?isAndroid=false&snd=true&token=testToken&msg=testMsg
            boolean isAndroid = request.queryParams("isAndroid").equalsIgnoreCase("true");
            boolean useSound = request.queryParams("snd").equalsIgnoreCase("true");
            String apsTokenHex = new String(Hex.decodeHex(request.queryParams("token").toCharArray()), "UTF-8");
            String encryptedMessage = new String(Hex.decodeHex(request.queryParams("msg").toCharArray()), "UTF-8");

            log.info("isAndroid={}\nuseSound={}\napsTokenHex={}\nencryptedMessage={}",
                isAndroid, useSound, apsTokenHex, encryptedMessage);
            return relayService.sendMessage(isAndroid, apsTokenHex, encryptedMessage, useSound);
        });
    }

    private static void keepRunning() {
        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignore) {
            }
        }
    }
}
