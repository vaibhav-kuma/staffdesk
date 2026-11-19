package com.staffdesk.desktop;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Real JavaFX rendering/validation and optional UI -> HTTP -> PostgreSQL CRUD round trip. */
@EnabledIfEnvironmentVariable(named = "RUN_FX_TESTS", matches = "true")
class FxSmokeTest {
  private Stage stage;
  private StaffdeskApp app;

  @Test
  void desktopWorkflow() throws Exception {
    System.setProperty(
        "STAFFDESK_API_URL", System.getenv().getOrDefault("FX_E2E_URL", "http://127.0.0.1:8080"));
    System.setProperty("STAFFDESK_ALLOW_LOCAL_HTTP", "true");
    CompletableFuture<Void> started = new CompletableFuture<>();
    Platform.startup(
        () -> {
          try {
            Platform.setImplicitExit(false);
            app = new StaffdeskApp();
            stage = new Stage();
            app.start(stage);
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            started.complete(null);
          } catch (Throwable t) {
            started.completeExceptionally(t);
          }
        });
    try {
      started.get(30, TimeUnit.SECONDS);
      fx(
          () -> {
            assertNotNull(stage.getScene().lookup("#loginEmail"));
            ((Button) find("#signIn")).fire();
            assertTrue(
                stage.getScene().getRoot().lookupAll(".error").stream()
                    .anyMatch(
                        n -> n instanceof Label l && l.getText().contains("Enter your email")));
            return null;
          });
      screenshot("login");
      if (System.getenv("FX_E2E_URL") != null) {
        fx(
            () -> {
              ((TextField) find("#loginEmail")).setText(System.getenv("FX_E2E_EMAIL"));
              ((PasswordField) find("#loginPassword")).setText(System.getenv("FX_E2E_PASSWORD"));
              ((Button) find("#signIn")).fire();
              return null;
            });
        await(
            () ->
                stage.getScene().lookup("#employeeTable") != null
                    && ((TableView<?>) find("#employeeTable")).getItems().isEmpty());
        Platform.runLater(() -> ((Button) find("#addEmployee")).fire());
        await(() -> find("#employeeFirstName") != null);
        fx(
            () -> {
              set("#employeeFirstName", "Asha");
              set("#employeeLastName", "Sharma");
              set("#employeeEmail", "asha.ui@example.test");
              set("#employeeDepartment", "Engineering");
              set("#employeeJobTitle", "Software Engineer");
              ((Button) find("#saveEmployee")).fire();
              return null;
            });
        await(
            () ->
                find("#saveEmployee") == null
                    && ((TableView<?>) find("#employeeTable")).getItems().size() == 1);
        screenshot("directory");
        Platform.runLater(() -> ((Button) find("#editEmployee")).fire());
        await(() -> find("#employeeFirstName") != null);
        fx(
            () -> {
              set("#employeeJobTitle", "Principal Engineer");
              ((Button) find("#saveEmployee")).fire();
              return null;
            });
        await(
            () ->
                find("#saveEmployee") == null
                    && ((com.staffdesk.contracts.Api.Employee)
                            ((TableView<?>) find("#employeeTable")).getItems().getFirst())
                        .jobTitle()
                        .equals("Principal Engineer"));
        Platform.runLater(() -> ((Button) find("#deleteEmployee")).fire());
        await(() -> Window.getWindows().size() > 1);
        fx(
            () -> {
              for (Window w : java.util.List.copyOf(Window.getWindows()))
                if (w != stage && w.isShowing())
                  for (Node n : w.getScene().getRoot().lookupAll(".button"))
                    if (n instanceof Button b && b.getText().equals("OK")) {
                      b.fire();
                      return null;
                    }
              throw new AssertionError("Delete confirmation not found");
            });
        await(() -> ((TableView<?>) find("#employeeTable")).getItems().isEmpty());
        fx(
            () -> {
              ((Button) find("#signOut")).fire();
              assertNotNull(find("#loginEmail"));
              return null;
            });
      }
    } finally {
      fx(
          () -> {
            stage.close();
            app.stop();
            return null;
          });
      Platform.exit();
    }
  }

  private Node find(String selector) {
    for (Window w : Window.getWindows())
      if (w.isShowing() && w.getScene() != null) {
        Node n = w.getScene().lookup(selector);
        if (n != null) return n;
      }
    return null;
  }

  private void set(String id, String value) {
    ((TextField) find(id)).setText(value);
  }

  private <T> T fx(Callable<T> action) throws Exception {
    CompletableFuture<T> result = new CompletableFuture<>();
    Platform.runLater(
        () -> {
          try {
            result.complete(action.call());
          } catch (Throwable e) {
            result.completeExceptionally(e);
          }
        });
    return result.get(30, TimeUnit.SECONDS);
  }

  private void await(BooleanSupplier condition) throws Exception {
    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (System.nanoTime() < until) {
      if (fx(condition::getAsBoolean)) return;
      Thread.sleep(100);
    }
    fail("Timed out waiting for desktop state");
  }

  private void screenshot(String name) throws Exception {
    var image = fx(() -> stage.getScene().snapshot(null));
    java.awt.image.BufferedImage png =
        new java.awt.image.BufferedImage(
            (int) image.getWidth(),
            (int) image.getHeight(),
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
    var pixels = image.getPixelReader();
    for (int y = 0; y < png.getHeight(); y++)
      for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, pixels.getArgb(x, y));
    Path dir = Path.of("target", "screenshots");
    Files.createDirectories(dir);
    javax.imageio.ImageIO.write(png, "png", dir.resolve(name + ".png").toFile());
  }
}
