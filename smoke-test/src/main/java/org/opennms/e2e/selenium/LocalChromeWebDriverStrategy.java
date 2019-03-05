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
import java.nio.file.Paths;
import java.util.Properties;

import org.opennms.e2e.core.WebDriverStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

public class LocalChromeWebDriverStrategy implements WebDriverStrategy {
    private ChromeDriverService service;
    private WebDriver driver;

    public LocalChromeWebDriverStrategy() throws IOException {
        loadSettings();

        service = new ChromeDriverService.Builder()
                .usingAnyFreePort()
                .build();
        service.start();
        driver = new RemoteWebDriver(service.getUrl(),
                DesiredCapabilities.chrome());
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
        if (service != null) {
            service.stop();
        }
    }

    @Override
    public boolean isInternallyNetworked() {
        return false;
    }

    private void loadSettings() {
        // Try the environment variables first
        String webdriverChromeDriver = System.getenv("WEBDRIVER_CHROME_DRIVER");

        if (webdriverChromeDriver != null) {
            System.setProperty("webdriver.chrome.driver", webdriverChromeDriver);
        } else {
            // Try the properties file
            String home = System.getProperty("user.home");
            File sauceProps = Paths.get(home, ".config", "chromedriver.properties").toFile();
            if (sauceProps.canRead()) {
                try (InputStream is = new FileInputStream(sauceProps)) {
                    Properties props = new Properties();
                    props.load(is);
                    webdriverChromeDriver = props.getProperty("webdriver.chrome.driver");

                    if (webdriverChromeDriver != null) {
                        System.setProperty("webdriver.chrome.driver", webdriverChromeDriver);
                    }
                } catch (IOException ignore) {
                }
            }
        }
    }
}
