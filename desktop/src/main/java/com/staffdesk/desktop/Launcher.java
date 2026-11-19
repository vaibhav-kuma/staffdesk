package com.staffdesk.desktop;

/** Separate launcher avoids the Java launcher special-casing Application on classpath. */
public final class Launcher {
  private Launcher() {}

  public static void main(String[] args) {
    javafx.application.Application.launch(StaffdeskApp.class, args);
  }
}
