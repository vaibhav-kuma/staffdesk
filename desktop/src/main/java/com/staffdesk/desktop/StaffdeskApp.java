package com.staffdesk.desktop;

import static com.staffdesk.desktop.ViewSupport.*;

import com.staffdesk.contracts.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.animation.PauseTransition;
import javafx.application.*;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;

/** Native, role-aware directory. Services hold HTTP concerns, this class holds view state only. */
public final class StaffdeskApp extends Application {
  private Stage stage;
  private ApiClient api;
  private Api.Token session;
  private final BorderPane shell = new BorderPane();
  private final Label notice = new Label();
  private final ProgressIndicator busy = new ProgressIndicator();
  private final TableView<Api.Employee> table = new TableView<>();
  private final TableView<Api.User> accounts = new TableView<>();
  private final TextField search = new TextField();
  private final ComboBox<String> department = new ComboBox<>(),
      status = new ComboBox<>(),
      sort = new ComboBox<>(),
      direction = new ComboBox<>();
  private final ComboBox<Integer> pageSize = new ComboBox<>();
  private final Label pageLabel = new Label(), accountPageLabel = new Label();
  private final Button previous = new Button("← Previous"),
      next = new Button("Next →"),
      usersPrevious = new Button("← Previous"),
      usersNext = new Button("Next →");
  private boolean filtersConfigured = false;
  private int page = 0, userPage = 0;
  private long generation = 0, userGeneration = 0, sessionGeneration = 0;
  private boolean accountsView = false;
  private final IntegerProperty pending = new SimpleIntegerProperty();
  private final PauseTransition debounce = new PauseTransition(Duration.millis(350));

  @Override
  public void start(Stage primary) {
    stage = primary;
    stage.setTitle("Staffdesk · People, connected");
    stage.setMinWidth(940);
    stage.setMinHeight(660);
    try {
      api =
          new ApiClient(
              setting("STAFFDESK_API_URL", "https://api.example.invalid"),
              Boolean.parseBoolean(setting("STAFFDESK_ALLOW_LOCAL_HTTP", "false")));
    } catch (IllegalArgumentException e) {
      new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.CLOSE).showAndWait();
      Platform.exit();
      return;
    }
    busy.setMaxSize(18, 18);
    busy.visibleProperty().bind(pending.greaterThan(0));
    notice.getStyleClass().add("notice");
    Scene scene = new Scene(shell, 1220, 800);
    scene
        .getStylesheets()
        .add(Objects.requireNonNull(getClass().getResource("staffdesk.css")).toExternalForm());
    stage.setScene(scene);
    scene
        .getAccelerators()
        .put(
            new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN),
            () -> search.requestFocus());
    configureTables();
    loginView();
    stage.show();
  }

  private static String setting(String key, String fallback) {
    String v = System.getProperty(key);
    return v != null ? v : System.getenv().getOrDefault(key, fallback);
  }

  private Button button(String text, String style, Runnable action) {
    Button b = new Button(text);
    if (text.contains("Add employee")) b.setId("addEmployee");
    if (text.equals("Edit")) b.setId("editEmployee");
    if (text.equals("Delete")) b.setId("deleteEmployee");
    if (text.startsWith("Sign out")) b.setId("signOut");
    b.getStyleClass().add(style);
    b.setOnAction(e -> action.run());
    return b;
  }

  private void loginView() {
    shell.setLeft(null);
    shell.setBottom(null);
    shell.setTop(null);
    Label brand = label("S /  STAFFDESK", "brand"),
        eyebrow = label("THE PEOPLE PLATFORM", "eyebrow");
    Label title = label("Good teams.\nGreat connections.", "hero");
    title.setWrapText(true);
    Label copy =
        label(
            "One secure place for your people directory.\nBuilt for the way your team works.",
            "hero-copy");
    VBox story =
        new VBox(
            28,
            brand,
            new Region(),
            eyebrow,
            title,
            copy,
            new Region(),
            label("DESKTOP EDITION     /     01", "eyebrow"));
    story.getStyleClass().add("login-story");
    story.setPrefWidth(490);
    VBox.setVgrow(story.getChildren().get(1), Priority.ALWAYS);
    VBox.setVgrow(story.getChildren().get(5), Priority.ALWAYS);
    TextField email = new TextField();
    email.setId("loginEmail");
    email.setPromptText("you@company.com");
    PasswordField password = new PasswordField();
    password.setId("loginPassword");
    password.setPromptText("Your password");
    Label error = label("", "error");
    error.setWrapText(true);
    Label endpoint = label(setting("STAFFDESK_API_URL", "https://api.example.invalid"), "muted");
    endpoint.setWrapText(true);
    Button signIn = new Button("Sign in  →");
    signIn.setId("signIn");
    signIn.getStyleClass().add("primary");
    signIn.setMaxWidth(Double.MAX_VALUE);
    signIn.setDefaultButton(true);
    signIn.setOnAction(
        e -> {
          if (email.getText().isBlank() || password.getText().isEmpty()) {
            error.setText("Enter your email and password.");
            return;
          }
          signIn.setDisable(true);
          error.setText("");
          String secret = password.getText();
          password.clear();
          api.login(email.getText().strip(), secret)
              .whenComplete(
                  (token, failure) ->
                      Platform.runLater(
                          () -> {
                            signIn.setDisable(false);
                            if (failure != null) {
                              error.setText(root(failure).getMessage());
                              return;
                            }
                            sessionGeneration++;
                            session = token;
                            page = 0;
                            userPage = 0;
                            directoryView();
                          }));
        });
    VBox form =
        new VBox(
            12,
            label("WELCOME BACK", "overline"),
            label("Sign in to Staffdesk", "page-title"),
            label("Use the account provided by your administrator.", "muted"),
            new Separator(),
            label("Work email", "field-label"),
            email,
            label("Password", "field-label"),
            password,
            error,
            signIn,
            new Separator(),
            label("API CONNECTION", "overline"),
            endpoint);
    form.setMaxWidth(370);
    VBox right = new VBox(form);
    right.setAlignment(Pos.CENTER);
    right.setPadding(new Insets(48));
    HBox.setHgrow(right, Priority.ALWAYS);
    HBox login = new HBox(story, right);
    shell.setCenter(login);
  }

  private void navigation() {
    Label identity = label(session.email(), "sidebar-copy");
    identity.setWrapText(true);
    Button people = button("◉   People directory", "nav", this::directoryView);
    people.setMaxWidth(Double.MAX_VALUE);
    VBox sidebar =
        new VBox(14, label("S /  STAFFDESK", "brand"), label("WORKSPACE", "eyebrow"), people);
    if (session.role() == Role.ADMIN) {
      Button users = button("▦   Account access", "nav", this::accountsView);
      users.setMaxWidth(Double.MAX_VALUE);
      sidebar.getChildren().add(users);
    }
    Region spacer = new Region();
    VBox.setVgrow(spacer, Priority.ALWAYS);
    sidebar
        .getChildren()
        .addAll(
            spacer,
            label(session.role().name(), "eyebrow"),
            identity,
            button("Sign out  ↗", "nav", this::logout));
    sidebar.getStyleClass().add("sidebar");
    sidebar.setPrefWidth(220);
    shell.setLeft(sidebar);
    HBox footer = new HBox(10, busy, notice);
    footer.getStyleClass().add("footer");
    shell.setBottom(footer);
  }

  private void directoryView() {
    accountsView = false;
    userGeneration++;
    navigation();
    Label title = label("People directory", "page-title");
    Label desc =
        label("Your team, in one place. Find people and keep their details up to date.", "muted");
    Region space = new Region();
    HBox.setHgrow(space, Priority.ALWAYS);
    HBox heading =
        new HBox(14, new VBox(8, label("WORKSPACE / PEOPLE", "overline"), title, desc), space);
    heading.setAlignment(Pos.CENTER_LEFT);
    if (session.role() != Role.VIEWER)
      heading.getChildren().add(button("+  Add employee", "primary", () -> employeeDialog(null)));
    search.setPromptText("Search name or email…  ⌘ / Ctrl F");
    search.setId("employeeSearch");
    search.setPrefWidth(300);
    HBox.setHgrow(search, Priority.ALWAYS);
    department.setOnAction(null);
    status.setOnAction(null);
    sort.setOnAction(null);
    direction.setOnAction(null);
    pageSize.setOnAction(null);
    department.setPromptText("All departments");
    department.setPrefWidth(175);
    if (department.getItems().isEmpty()) {
      department.getItems().setAll("All departments");
      department.setValue("All departments");
    }
    status.getItems().setAll("All statuses", "ACTIVE", "ON_LEAVE", "INACTIVE");
    if (status.getValue() == null) status.setValue("All statuses");
    sort.getItems()
        .setAll(
            "lastName",
            "firstName",
            "email",
            "department",
            "jobTitle",
            "hireDate",
            "status",
            "updatedAt");
    if (sort.getValue() == null) sort.setValue("lastName");
    direction.getItems().setAll("asc", "desc");
    if (direction.getValue() == null) direction.setValue("asc");
    if (!filtersConfigured) {
      filtersConfigured = true;
      search
          .textProperty()
          .addListener(
              (o, a, b) -> {
                debounce.setOnFinished(e -> reloadFromStart());
                debounce.playFromStart();
              });
    }
    department.setOnAction(e -> reloadFromStart());
    status.setOnAction(e -> reloadFromStart());
    sort.setOnAction(e -> reloadFromStart());
    direction.setOnAction(e -> reloadFromStart());
    FlowPane filters =
        new FlowPane(
            12,
            12,
            search,
            department,
            status,
            button(
                "↻ Refresh",
                "secondary",
                () -> {
                  loadEmployees();
                  loadDepartments();
                }));
    filters.setAlignment(Pos.CENTER_LEFT);
    desc.setWrapText(true);
    pageSize.getItems().setAll(10, 25, 50, 100);
    if (pageSize.getValue() == null) pageSize.setValue(25);
    pageSize.setOnAction(e -> reloadFromStart());
    previous.setOnAction(
        e -> {
          page--;
          loadEmployees();
        });
    next.setOnAction(
        e -> {
          page++;
          loadEmployees();
        });
    HBox pagination =
        new HBox(12, pageLabel, new Region(), label("Rows", "muted"), pageSize, previous, next);
    HBox.setHgrow(pagination.getChildren().get(1), Priority.ALWAYS);
    pagination.setAlignment(Pos.CENTER_LEFT);
    HBox sorting = new HBox(10, label("SORT BY", "overline"), sort, direction);
    sorting.setAlignment(Pos.CENTER_LEFT);
    VBox card = new VBox(16, filters, sorting, table, pagination);
    card.getStyleClass().add("card");
    VBox.setVgrow(table, Priority.ALWAYS);
    VBox content = new VBox(28, heading, card);
    content.getStyleClass().add("content");
    VBox.setVgrow(card, Priority.ALWAYS);
    shell.setCenter(content);
    notice.setText(
        (setting("STAFFDESK_API_URL", "").startsWith("https://")
                ? "Secure connection · "
                : "Local development (HTTP) · ")
            + session.role().name().toLowerCase(Locale.ROOT)
            + " access");
    loadEmployees();
    loadDepartments();
  }

  private void configureTables() {
    table.setId("employeeTable");
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setPlaceholder(
        new Label("No employees found. Try another search or add your first employee."));
    TableColumn<Api.Employee, String> name = new TableColumn<>("EMPLOYEE");
    name.setCellValueFactory(
        c -> new SimpleStringProperty(c.getValue().firstName() + " " + c.getValue().lastName()));
    name.setCellFactory(
        c ->
            new TableCell<>() {
              @Override
              protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(null);
                setGraphic(null);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) return;
                Api.Employee p = getTableRow().getItem();
                Label avatar =
                    label(
                        (p.firstName().substring(0, 1) + p.lastName().substring(0, 1))
                            .toUpperCase(Locale.ROOT),
                        "avatar");
                HBox row =
                    new HBox(
                        12,
                        avatar,
                        new VBox(4, label(v, "person-name"), label(p.email(), "muted")));
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
              }
            });
    name.setPrefWidth(310);
    table.getColumns().add(name);
    column(table, "DEPARTMENT", e -> e.department(), 160);
    column(table, "JOB TITLE", e -> e.jobTitle(), 180);
    TableColumn<Api.Employee, String> state = new TableColumn<>("STATUS");
    state.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status().name()));
    state.setCellFactory(
        c ->
            new TableCell<>() {
              @Override
              protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(null);
                setGraphic(null);
                if (!empty) {
                  Label badge = label(v.replace('_', ' '), "badge");
                  badge.getStyleClass().add("badge-" + v.toLowerCase(Locale.ROOT));
                  setGraphic(badge);
                }
              }
            });
    table.getColumns().add(state);
    TableColumn<Api.Employee, Void> actions = new TableColumn<>("");
    actions.setPrefWidth(150);
    actions.setCellFactory(
        c ->
            new TableCell<>() {
              private final Button edit =
                  button("Edit", "text-button", () -> employeeDialog(getTableRow().getItem()));
              private final Button del =
                  button("Delete", "danger-text", () -> deleteEmployee(getTableRow().getItem()));

              @Override
              protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(
                    empty || session == null || session.role() == Role.VIEWER
                        ? null
                        : new HBox(6, edit, del));
              }
            });
    table.getColumns().add(actions);
    table.getColumns().forEach(c -> c.setSortable(false));
    table.setRowFactory(
        t -> {
          var row = new TableRow<Api.Employee>();
          row.setOnMouseClicked(
              e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) employeeDialog(row.getItem());
              });
          return row;
        });
    accounts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    accounts.setPlaceholder(new Label("No accounts"));
    column(accounts, "EMAIL", Api.User::email, 330);
    column(accounts, "ROLE", u -> u.role().name(), 130);
    column(accounts, "STATUS", u -> u.enabled() ? "Enabled" : "Disabled", 110);
    TableColumn<Api.User, Void> manage = new TableColumn<>("ACCESS");
    manage.setCellFactory(
        c ->
            new TableCell<>() {
              @Override
              protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(
                    empty
                        ? null
                        : new HBox(
                            10,
                            button(
                                "Manage", "text-button", () -> userDialog(getTableRow().getItem())),
                            button(
                                "Reset password",
                                "text-button",
                                () -> resetPassword(getTableRow().getItem()))));
              }
            });
    accounts.getColumns().add(manage);
    accounts.getColumns().forEach(c -> c.setSortable(false));
  }

  private <T> void column(TableView<T> view, String title, Function<T, String> getter, int width) {
    TableColumn<T, String> c = new TableColumn<>(title);
    c.setCellValueFactory(v -> new SimpleStringProperty(getter.apply(v.getValue())));
    c.setPrefWidth(width);
    view.getColumns().add(c);
  }

  private void reloadFromStart() {
    page = 0;
    loadEmployees();
  }

  private void loadEmployees() {
    if (session == null || accountsView || pageSize.getValue() == null) return;
    long request = ++generation;
    previous.setDisable(true);
    next.setDisable(true);
    run(
        api.employees(
            search.getText(),
            Objects.equals(department.getValue(), "All departments")
                ? ""
                : Objects.toString(department.getValue(), ""),
            Objects.equals(status.getValue(), "All statuses")
                ? ""
                : Objects.toString(status.getValue(), ""),
            page,
            pageSize.getValue(),
            sort.getValue(),
            direction.getValue()),
        p -> {
          if (request != generation || accountsView) return;
          if (page > 0 && page >= p.totalPages()) {
            page = Math.max(0, p.totalPages() - 1);
            loadEmployees();
            return;
          }
          table.getItems().setAll(p.content());
          pageLabel.setText(
              p.totalElements()
                  + " people  ·  Page "
                  + (p.totalPages() == 0 ? 0 : page + 1)
                  + " of "
                  + p.totalPages());
          previous.setDisable(page == 0);
          next.setDisable(page + 1 >= p.totalPages());
        });
  }

  private void loadDepartments() {
    run(
        api.departments(),
        items -> {
          String selected = department.getValue();
          department.setOnAction(null);
          department.getItems().setAll("All departments");
          department.getItems().addAll(items);
          department.setValue(items.contains(selected) ? selected : "All departments");
          department.setOnAction(e -> reloadFromStart());
        });
  }

  private void employeeDialog(Api.Employee old) {
    long epoch = sessionGeneration;
    EmployeeDialog.show(
        stage,
        api,
        session.role(),
        old,
        () -> epoch == sessionGeneration,
        this::unauthorized,
        () -> {
          notice.setText(old == null ? "Employee created." : "Changes saved.");
          loadEmployees();
          loadDepartments();
        });
  }

  private void deleteEmployee(Api.Employee e) {
    if (e == null) return;
    Alert confirm =
        new Alert(
            Alert.AlertType.CONFIRMATION,
            "Permanently delete " + e.firstName() + " " + e.lastName() + "? This cannot be undone.",
            ButtonType.CANCEL,
            ButtonType.OK);
    confirm.initOwner(stage);
    confirm.setHeaderText("Delete employee");
    if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
      run(
          api.delete(e),
          v -> {
            notice.setText("Employee deleted.");
            loadEmployees();
            loadDepartments();
          });
  }

  private void accountsView() {
    accountsView = true;
    generation++;
    navigation();
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox heading =
        new HBox(
            20,
            new VBox(
                8,
                label("WORKSPACE / SECURITY", "overline"),
                label("Access & accounts", "page-title"),
                label("Manage who can sign in and what they can do.", "muted")),
            spacer,
            button("+  Add account", "primary", () -> userDialog(null)));
    heading.setAlignment(Pos.CENTER_LEFT);
    usersPrevious.setOnAction(
        e -> {
          userPage--;
          loadUsers();
        });
    usersNext.setOnAction(
        e -> {
          userPage++;
          loadUsers();
        });
    HBox pages = new HBox(12, accountPageLabel, new Region(), usersPrevious, usersNext);
    HBox.setHgrow(pages.getChildren().get(1), Priority.ALWAYS);
    VBox card = new VBox(18, accounts, pages);
    card.getStyleClass().add("card");
    VBox.setVgrow(accounts, Priority.ALWAYS);
    VBox content = new VBox(28, heading, card);
    content.getStyleClass().add("content");
    VBox.setVgrow(card, Priority.ALWAYS);
    shell.setCenter(content);
    loadUsers();
  }

  private void loadUsers() {
    long request = ++userGeneration;
    usersPrevious.setDisable(true);
    usersNext.setDisable(true);
    run(
        api.users(userPage),
        p -> {
          if (!accountsView || request != userGeneration) return;
          if (userPage > 0 && userPage >= p.totalPages()) {
            userPage = Math.max(0, p.totalPages() - 1);
            loadUsers();
            return;
          }
          accounts.getItems().setAll(p.content());
          accountPageLabel.setText(
              p.totalElements() + " accounts · Page " + (userPage + 1) + " of " + p.totalPages());
          usersPrevious.setDisable(userPage == 0);
          usersNext.setDisable(userPage + 1 >= p.totalPages());
        });
  }

  private void userDialog(Api.User old) {
    Dialog<ButtonType> d = dialog(old == null ? "Create account" : "Manage account");
    TextField email = field(old == null ? "" : old.email());
    email.setDisable(old != null);
    PasswordField pw = new PasswordField();
    pw.setPromptText("At least 12 characters, at most 72 UTF-8 bytes");
    ComboBox<Role> role = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
    role.setValue(old == null ? Role.VIEWER : old.role());
    CheckBox enabled = new CheckBox("Account enabled");
    enabled.setSelected(old == null || old.enabled());
    Label error = label("", "error");
    error.setWrapText(true);
    VBox content =
        new VBox(12, label("Email", "field-label"), email, label("Role", "field-label"), role);
    if (old == null) content.getChildren().addAll(label("Initial password", "field-label"), pw);
    else content.getChildren().add(enabled);
    content
        .getChildren()
        .addAll(
            label("VIEWER reads · EDITOR manages people · ADMIN manages access", "muted"), error);
    content.setPrefWidth(510);
    d.getDialogPane().setContent(content);
    Button save = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
    save.setText("Save account");
    save.getStyleClass().add("primary");
    save.addEventFilter(
        javafx.event.ActionEvent.ACTION,
        e -> {
          e.consume();
          save.setDisable(true);
          long epoch = sessionGeneration;
          CompletableFuture<Api.User> request =
              old == null
                  ? api.createUser(new Api.NewUser(email.getText(), pw.getText(), role.getValue()))
                  : api.updateUser(
                      old.id(), new Api.UserUpdate(role.getValue(), enabled.isSelected()));
          pw.clear();
          request.whenComplete(
              (u, f) ->
                  Platform.runLater(
                      () -> {
                        save.setDisable(false);
                        if (epoch != sessionGeneration) {
                          d.close();
                          return;
                        }
                        if (f != null) {
                          if (unauthorized(f)) {
                            d.close();
                            return;
                          }
                          error.setText(root(f).getMessage());
                          return;
                        }
                        d.close();
                        if (old != null && old.id().equals(session.userId())) {
                          logout();
                          return;
                        }
                        loadUsers();
                      }));
        });
    d.showAndWait();
  }

  private void resetPassword(Api.User user) {
    Dialog<ButtonType> d = dialog("Reset password · " + user.email());
    PasswordField pw = new PasswordField();
    Label error = label("", "error");
    error.setWrapText(true);
    d.getDialogPane()
        .setContent(
            new VBox(
                12,
                label("New password (12+ characters, max 72 UTF-8 bytes)", "field-label"),
                pw,
                label("This immediately invalidates the account's existing sessions.", "muted"),
                error));
    Button save = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
    save.setText("Reset password");
    save.addEventFilter(
        javafx.event.ActionEvent.ACTION,
        e -> {
          e.consume();
          save.setDisable(true);
          long epoch = sessionGeneration;
          var request = api.password(user.id(), pw.getText());
          pw.clear();
          request.whenComplete(
              (v, f) ->
                  Platform.runLater(
                      () -> {
                        save.setDisable(false);
                        if (epoch != sessionGeneration) {
                          d.close();
                          return;
                        }
                        if (f != null) {
                          if (unauthorized(f)) {
                            d.close();
                            return;
                          }
                          error.setText(root(f).getMessage());
                          return;
                        }
                        d.close();
                        if (user.id().equals(session.userId())) logout();
                        else notice.setText("Password reset. Share it securely with the user.");
                      }));
        });
    d.showAndWait();
  }

  private Dialog<ButtonType> dialog(String title) {
    return ViewSupport.dialog(stage, title);
  }

  private <T> void run(CompletableFuture<T> future, Consumer<T> success) {
    long epoch = sessionGeneration;
    pending.set(pending.get() + 1);
    future.whenComplete(
        (value, failure) ->
            Platform.runLater(
                () -> {
                  pending.set(Math.max(0, pending.get() - 1));
                  if (epoch != sessionGeneration) return;
                  if (failure != null) {
                    if (!unauthorized(failure)) {
                      notice.setText("Request failed. Try refreshing.");
                      Alert a =
                          new Alert(
                              Alert.AlertType.ERROR, root(failure).getMessage(), ButtonType.OK);
                      a.initOwner(stage);
                      a.setHeaderText("Could not complete the request");
                      a.show();
                    }
                    return;
                  }
                  success.accept(value);
                }));
  }

  private boolean unauthorized(Throwable t) {
    if (root(t) instanceof ApiClient.ApiFailure f && f.status == 401) {
      logout();
      return true;
    }
    return false;
  }

  private static Throwable root(Throwable t) {
    while (t.getCause() != null
        && (t instanceof CompletionException || t instanceof ExecutionException)) t = t.getCause();
    return t;
  }

  private void logout() {
    sessionGeneration++;
    generation++;
    userGeneration++;
    debounce.stop();
    api.clearToken();
    session = null;
    table.getItems().clear();
    accounts.getItems().clear();
    search.clear();
    department.getItems().clear();
    loginView();
  }

  @Override
  public void stop() {
    if (api != null) api.close();
  }
}
