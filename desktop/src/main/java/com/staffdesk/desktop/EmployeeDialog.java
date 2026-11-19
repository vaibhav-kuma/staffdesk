package com.staffdesk.desktop;

import static com.staffdesk.desktop.ViewSupport.*;

import com.staffdesk.contracts.*;
import java.time.LocalDate;
import java.util.Objects;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/** Employee editor owns local validation and form state, not directory navigation. */
final class EmployeeDialog {
  private EmployeeDialog() {}

  static void show(
      Stage stage,
      ApiClient api,
      Role role,
      Api.Employee old,
      java.util.function.BooleanSupplier current,
      java.util.function.Predicate<Throwable> unauthorized,
      Runnable onSaved) {
    boolean readOnly = role == Role.VIEWER;
    Dialog<ButtonType> dialog =
        ViewSupport.dialog(
            stage, readOnly ? "Employee details" : old == null ? "Add employee" : "Edit employee");
    TextField first = field(old == null ? "" : old.firstName()),
        last = field(old == null ? "" : old.lastName()),
        email = field(old == null ? "" : old.email()),
        dept = field(old == null ? "" : old.department()),
        job = field(old == null ? "" : old.jobTitle()),
        phone = field(old == null ? "" : Objects.toString(old.phone(), ""));
    first.setId("employeeFirstName");
    last.setId("employeeLastName");
    email.setId("employeeEmail");
    dept.setId("employeeDepartment");
    job.setId("employeeJobTitle");
    phone.setId("employeePhone");
    DatePicker hire = new DatePicker(old == null ? LocalDate.now() : old.hireDate());
    hire.setEditable(false);
    ComboBox<EmployeeStatus> state =
        new ComboBox<>(FXCollections.observableArrayList(EmployeeStatus.values()));
    state.setValue(old == null ? EmployeeStatus.ACTIVE : old.status());
    GridPane grid = new GridPane();
    grid.setHgap(18);
    grid.setVgap(12);
    grid.setPrefWidth(540);
    addField(grid, 0, 0, "First name", first);
    addField(grid, 1, 0, "Last name", last);
    addField(grid, 0, 2, "Work email", email);
    addField(grid, 1, 2, "Department", dept);
    addField(grid, 0, 4, "Job title", job);
    addField(grid, 1, 4, "Phone (optional)", phone);
    addField(grid, 0, 6, "Hire date", hire);
    addField(grid, 1, 6, "Status", state);
    Label error = label("", "error");
    error.setWrapText(true);
    dialog
        .getDialogPane()
        .setContent(
            new VBox(
                15,
                label(
                    "Employee information stays in your organization's secure directory.", "muted"),
                grid,
                error));
    if (readOnly) {
      grid.setDisable(true);
      dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
      dialog.showAndWait();
      return;
    }
    Button save = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
    save.setId("saveEmployee");
    save.setText(old == null ? "Create employee" : "Save changes");
    save.getStyleClass().add("primary");
    save.addEventFilter(
        javafx.event.ActionEvent.ACTION,
        e -> {
          e.consume();
          if (first.getText().isBlank()
              || last.getText().isBlank()
              || dept.getText().isBlank()
              || job.getText().isBlank()
              || !email.getText().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
              || hire.getValue() == null
              || hire.getValue().isAfter(LocalDate.now())) {
            error.setText(
                "Complete required fields, enter a valid email and choose a hire date no later than today.");
            return;
          }
          save.setDisable(true);
          Api.EmployeeInput input =
              new Api.EmployeeInput(
                  first.getText(),
                  last.getText(),
                  email.getText(),
                  dept.getText(),
                  job.getText(),
                  phone.getText(),
                  hire.getValue(),
                  state.getValue(),
                  old == null ? null : old.version());
          api.save(old, input)
              .whenComplete(
                  (v, f) ->
                      Platform.runLater(
                          () -> {
                            save.setDisable(false);
                            if (!current.getAsBoolean()) {
                              dialog.close();
                              return;
                            }
                            if (f != null) {
                              if (unauthorized.test(f)) {
                                dialog.close();
                                return;
                              }
                              error.setText(ViewSupport.root(f).getMessage());
                              return;
                            }
                            dialog.close();
                            onSaved.run();
                          }));
        });
    dialog.showAndWait();
  }
}
