package app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ui.MainUI;

public class MainApp extends Application {

  @Override
  public void start(Stage stage) {
    MainUI mainUI = new MainUI();
    Scene scene = new Scene(mainUI.getRoot(), 980, 720);
    stage.setTitle("Real-Time Multi-Language Speech Translator (Vosk)");
    stage.setScene(scene);
    stage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}

