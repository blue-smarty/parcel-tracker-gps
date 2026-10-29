package com.parceltracker.controller;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

public final class AdminLoginDialog {
    public record Credentials(String username, String password) { }

    private AdminLoginDialog() { }

    public static Optional<Credentials> showAndWait(Window owner) {
        Dialog<Credentials> dialog = new Dialog<>();
        dialog.setTitle("Admin sign in");
        dialog.setHeaderText("Sign in to the dispatcher workspace");
        if (owner != null) dialog.initOwner(owner);

        ButtonType signIn = new ButtonType("Sign in", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(signIn, ButtonType.CANCEL);

        TextField username = new TextField();
        username.setPromptText("Admin username");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(10);
        fields.setPadding(new Insets(12));
        fields.add(new Label("Username"), 0, 0);
        fields.add(username, 1, 0);
        fields.add(new Label("Password"), 0, 1);
        fields.add(password, 1, 1);
        dialog.getDialogPane().setContent(fields);
        dialog.setResultConverter(button -> button == signIn
                ? new Credentials(username.getText().trim(), password.getText())
                : null);
        return dialog.showAndWait();
    }
}
