package com.staffdesk.desktop;

import java.util.Objects;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/** Shared native view factories. All callers execute on the FX application thread. */
final class ViewSupport {
  private ViewSupport() {}

  static Label label(String text, String style) {
    Label l = new Label(text);
    l.getStyleClass().add(style);
    return l;
  }

  static Dialog<ButtonType> dialog(Stage stage, String title) {
    Dialog<ButtonType> d = new Dialog<>();
    d.initOwner(stage);
    d.setTitle(title);
    d.getDialogPane()
        .getStylesheets()
        .add(
            Objects.requireNonNull(ViewSupport.class.getResource("staffdesk.css"))
                .toExternalForm());
    d.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
    return d;
  }

  static TextField field(String value) {
    TextField f = new TextField(value);
    f.setPrefWidth(250);
    return f;
  }

  static void addField(GridPane g, int col, int row, String caption, Control control) {
    g.add(label(caption, "field-label"), col, row);
    g.add(control, col, row + 1);
    control.setMaxWidth(Double.MAX_VALUE);
    GridPane.setHgrow(control, Priority.ALWAYS);
  }

  static Throwable root(Throwable t) {
    while (t.getCause() != null
        && (t instanceof java.util.concurrent.CompletionException
            || t instanceof java.util.concurrent.ExecutionException)) t = t.getCause();
    return t;
  }
}
