/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.e2e.selenium;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.opennms.e2e.core.WebDriverStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SauceLabsWebDriverStrategy implements WebDriverStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(SauceLabsWebDriverStrategy.class);

    private final WebDriver driver;
    private final String sessionId;
    private final String jobName;
    private boolean failed;
    private String username;
    private String accessKey;
    private String tunnelId;

    public SauceLabsWebDriverStrategy(String jobName) throws IOException {
        this.jobName = Objects.requireNonNull(jobName);

        loadSettings();
        String URL = "https://" + username + ":" + accessKey + "@ondemand.saucelabs.com:443/wd/hub";

        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setCapability(CapabilityType.PLATFORM, "Windows 10");
        caps.setCapability(CapabilityType.BROWSER_NAME, "chrome");
        caps.setCapability(CapabilityType.VERSION, "69.0");
        caps.setCapability("screenResolution", "1920x1080");
        if (tunnelId != null) {
            caps.setCapability("tunnelIdentifier", tunnelId);
        }

        caps.setCapability("name", jobName);

        // Set the circleci properties if present
        String circleBuildNum = System.getenv("CIRCLE_BUILD_NUM");
        if (circleBuildNum != null) {
            caps.setCapability("build", "circle: " + circleBuildNum);
        }

        String circleBuildURL = System.getenv("CIRCLE_BUILD_URL");
        if (circleBuildURL != null) {
            Map<String, String> customDataMap = new HashMap<>();
            customDataMap.put("url", circleBuildURL);
            caps.setCapability("customData", customDataMap);
        }

        driver = new RemoteWebDriver(new URL(URL), caps);

        sessionId = (((RemoteWebDriver) driver).getSessionId()).toString();
    }

    @Override
    public WebDriver getDriver() {
        return driver;
    }

    @Override
    public void close() {
        if (driver != null) {
            driver.quit();
        }
        try {
            SauceUtils.UpdateResults(username, accessKey, !failed, sessionId);
        } catch (Exception e) {
            LOG.error("Failed to report results.", e);
        }
        System.out.println("SauceOnDemandSessionID=" + sessionId + "job-name=" + jobName);
    }

    @Override
    public boolean isInternallyNetworked() {
        return false;
    }

    private void loadSettings() {
        // Try the environment variables first
        String username = System.getenv("SAUCE_USERNAME");
        String accessKey = System.getenv("SAUCE_ACCESS_KEY");
        String tunnelId = System.getenv("SAUCE_TUNNEL_ID");
        if (username != null && accessKey != null) {
            this.username = username;
            this.accessKey = accessKey;
            this.tunnelId = tunnelId;
            return;
        }

        // Try the properties file
        String home = System.getProperty("user.home");
        File sauceProps = Paths.get(home, ".config", "saucelabs.properties").toFile();
        if (sauceProps.canRead()) {
            try (InputStream is = new FileInputStream(sauceProps)) {
                Properties props = new Properties();
                props.load(is);
                username = props.getProperty("username");
                accessKey = props.getProperty("access-key");
                tunnelId = props.getProperty("tunnel-id");
                if (username != null && accessKey != null) {
                    this.username = username;
                    this.accessKey = accessKey;
                    this.tunnelId = tunnelId;
                    return;
                }
            } catch (IOException e) {
                LOG.warn("Failed to load properties file {}.", sauceProps, e);
            }
        }

        // No creds!
        throw new IllegalStateException("Could not find SauceLabs username and access key.");
    }

    @Override
    public void setFailed(boolean didFail) {
        failed = didFail;
    }
}
