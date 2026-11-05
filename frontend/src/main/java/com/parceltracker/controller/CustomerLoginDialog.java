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

public final class CustomerLoginDialog {
    public record Credentials(String email, String password) { }

    private CustomerLoginDialog() { }

    public static Optional<Credentials> showAndWait(Window owner) {
        Dialog<Credentials> dialog = new Dialog<>();
        dialog.setTitle("Customer sign in");
        dialog.setHeaderText("Sign in to access your deliveries");
        if (owner != null) dialog.initOwner(owner);

        ButtonType signIn = new ButtonType("Sign in", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(signIn, ButtonType.CANCEL);

        TextField email = new TextField();
        email.setPromptText("Account email");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(10);
        fields.setPadding(new Insets(12));
        fields.add(new Label("Email"), 0, 0);
        fields.add(email, 1, 0);
        fields.add(new Label("Password"), 0, 1);
        fields.add(password, 1, 1);
        dialog.getDialogPane().setContent(fields);
        dialog.setResultConverter(button -> button == signIn
                ? new Credentials(email.getText().trim(), password.getText())
                : null);
        return dialog.showAndWait();
    }
}
