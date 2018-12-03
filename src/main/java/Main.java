import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Stage;
import javafx.application.Application;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Draft");

        final Form form = new Form();

        final Scene scene = new Scene(form);

        primaryStage.setScene(scene);
        primaryStage.show();

        final KeyCodeCombination fullScreenExitKey = new KeyCodeCombination(KeyCode.F11);
        primaryStage.setFullScreenExitKeyCombination(fullScreenExitKey);
        primaryStage.setFullScreenExitHint("Для выхода из полноэкранного режима нажмите F11");

        final boolean[] fullscreen = {false};
        scene.getAccelerators().put(fullScreenExitKey, () -> {
            fullscreen[0] = !fullscreen[0];
            form.setFullScreen(fullscreen[0]);
            primaryStage.setFullScreen(fullscreen[0]);
        });

        primaryStage.setMaximized(true);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
