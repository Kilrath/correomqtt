package org.correomqtt;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.correomqtt.business.dispatcher.*;
import org.correomqtt.business.model.SettingsDTO;
import org.correomqtt.business.services.ConfigService;
import org.correomqtt.business.utils.VersionUtils;
import org.correomqtt.gui.controller.AlertController;
import org.correomqtt.gui.controller.MainViewController;
import org.correomqtt.gui.helper.AlertHelper;
import org.correomqtt.gui.utils.CheckNewVersionUtils;
import org.correomqtt.gui.utils.HostServicesHolder;
import org.correomqtt.plugin.PluginSystem;
import org.correomqtt.plugin.manager.PluginManager;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;

public class CorreoMqtt extends Application implements StartupObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorreoMqtt.class);
    private ResourceBundle resources;
    private MainViewController mainViewController;
    private Scene scene;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() throws IOException {
        LOGGER.info("Application started.");
        LOGGER.info("JVM: {} | {} | {}.", System.getProperty("java.vendor"), System.getProperty("java.runtime.name"), System.getProperty("java.runtime.version"));
        LOGGER.info("CorreoMQTT version is {}.", VersionUtils.getVersion());

        StartupDispatcher.getInstance().addObserver(this);

        final SettingsDTO settings = ConfigService.getInstance().getSettings();

        handleVersionMismatch(settings);
        setLocale(settings);
        HostServicesHolder.getInstance().setHostServices(getHostServices());

        PreloadingDispatcher.getInstance().onProgress(resources.getString("preloaderLanguageSet"));

        if (settings.isFirstStart()) {
            initUpdatesOnFirstStart(settings);
        }

        if (settings.isSearchUpdates()) {
            PreloadingDispatcher.getInstance().onProgress(resources.getString("preloaderSearchingUpdates"));
            new PluginSystem().start();
            checkForUpdates();
        }

        PreloadingDispatcher.getInstance().onProgress(resources.getString("preloaderReady"));
        ConfigService.getInstance().saveSettings();
    }

    private void handleVersionMismatch(SettingsDTO settings) {
        if (settings.getConfigCreatedWithCorreoVersion() == null) {
            LOGGER.info("Setting initial correo version in settings: {}", VersionUtils.getVersion());
            settings.setConfigCreatedWithCorreoVersion(VersionUtils.getVersion());
        } else if (new ComparableVersion(VersionUtils.getVersion())
                .compareTo(new ComparableVersion(settings.getConfigCreatedWithCorreoVersion())) > 0) {
            LOGGER.info("Installed version is newer than version which created the config file");
            // handle issues if new version needs some changes
        }
    }

    private void initUpdatesOnFirstStart(SettingsDTO settings) {
        boolean checkForUpdates = AlertHelper.confirm(
                resources.getString("settingsViewUpdateLabel"),
                null,
                resources.getString("firstStartCheckForUpdatesTitle"),
                resources.getString("commonNoButton"),
                resources.getString("commonYesButton")
        );

        settings.setFirstStart(false);
        settings.setSearchUpdates(checkForUpdates);
    }

    private void setLocale(SettingsDTO settings) {
        if (settings.getSavedLocale() == null) {
            if (Locale.getDefault().getLanguage().equals("de") &&
                    Locale.getDefault().getCountry().equals("DE")) {
                settings.setSavedLocale(new Locale("de", "DE"));
            } else {
                settings.setSavedLocale(new Locale("en", "US"));
            }
        }
        settings.setCurrentLocale(settings.getSavedLocale());
        LOGGER.info("Locale is: {}", settings.getSavedLocale());
        resources = ResourceBundle.getBundle("org.correomqtt.i18n", ConfigService.getInstance().getSettings().getCurrentLocale());
    }

    private void checkForUpdates() {
        try {
            CheckNewVersionUtils.checkNewVersion(false);
        } catch (IOException | ParseException e) {
            LOGGER.warn("Version check failed.", e);
        }
    }



    @Override
    public void start(Stage primaryStage) throws Exception {
        loadPrimaryStage(primaryStage);

        AlertController.activate();
    }

    private void loadPrimaryStage(Stage primaryStage) throws IOException {
        ConfigService.getInstance().setCssFileName();
        String cssPath = ConfigService.getInstance().getCssPath();

        FXMLLoader loader = new FXMLLoader(MainViewController.class.getResource("mainView.fxml"),
                                           ResourceBundle.getBundle("org.correomqtt.i18n", ConfigService.getInstance().getSettings().getCurrentLocale()));
        Parent root = loader.load();

        mainViewController = loader.getController();
        primaryStage.setTitle("CorreoMQTT v" + VersionUtils.getVersion());
        scene = new Scene(root, 900, 800);

        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        primaryStage.setScene(scene);
        primaryStage.setMinHeight(400);
        primaryStage.setMinWidth(850);
        primaryStage.show();

        primaryStage.setOnCloseRequest(t -> {
            LOGGER.info("Main window closed. Initialize shutdown.");
            LOGGER.info("Shutting down connections.");
            ApplicationLifecycleDispatcher.getInstance().onShutdown();
            LOGGER.info("Shutting down plugins.");
            PluginManager.getInstance().stopPlugins();
            LOGGER.info("Shutting down application. Bye.");
            Platform.exit();
            System.exit(0);
        });

        setupShortcut();
    }

    private void setupShortcut() {
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {

                                  if (event.getCode().equals(KeyCode.S) && event.isShortcutDown() && !event.isShiftDown()) {
                                      ShortcutDispatcher.getInstance().onSubscriptionShortcutPressed(mainViewController.getUUIDofSelectedTab());
                                      event.consume();
                                  }
                                  if (event.getCode().equals(KeyCode.S) && event.isShortcutDown() && event.isShiftDown()) {
                                      ShortcutDispatcher.getInstance().onClearIncomingShortcutPressed(mainViewController.getUUIDofSelectedTab());
                                      event.consume();
                                  }
                                  if (event.getCode().equals(KeyCode.P) && event.isShortcutDown() && !event.isShiftDown()) {
                                      ShortcutDispatcher.getInstance().onPublishShortcutPressed(mainViewController.getUUIDofSelectedTab());
                                      event.consume();
                                  }
                                  if (event.getCode().equals(KeyCode.P) && event.isShortcutDown() && event.isShiftDown()) {
                                      ShortcutDispatcher.getInstance().onClearOutgoingShortcutPressed(mainViewController.getUUIDofSelectedTab());
                                      event.consume();
                                  }
                                  //TODO rest
                              }
        );
    }

    @Override
    public void onPluginUpdateFailed(String disabledPath) {
        AlertHelper.warn(
                resources.getString("pluginUpdateErrorTitle"),
                resources.getString("pluginUpdateErrorContent") + " " + disabledPath,
                true
        );
    }

    @Override
    public void onPluginLoadFailed() {
        AlertHelper.warn(
                resources.getString("pluginErrorTitle"),
                resources.getString("pluginErrorContent"),
                true,
                new ButtonType(resources.getString("closeNow"), ButtonBar.ButtonData.OK_DONE)
        );
        System.exit(1);
    }

}
