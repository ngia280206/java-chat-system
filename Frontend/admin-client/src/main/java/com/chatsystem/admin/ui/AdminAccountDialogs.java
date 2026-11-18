package com.chatsystem.admin.ui;

import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.service.AdminService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.util.Optional;

/** In-memory V1 admin account dialogs; no server or persistent database. */
public final class AdminAccountDialogs {
    private AdminAccountDialogs() {}
    private static void style(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(
                AdminAccountDialogs.class.getResource("/css/dark.css").toExternalForm());
        dialog.getDialogPane().setStyle("-fx-background-color: #15212E;");
    }
    public static Optional<UserAccount> showEditor(AdminService service, UserAccount user) {
        boolean creating = user == null;
        Dialog<UserAccount> dialog = new Dialog<>();
        style(dialog);
        dialog.setTitle(creating ? "Add user" : "Edit user");
        ButtonType save = new ButtonType(creating ? "Add user" : "Save changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(save, ButtonType.CANCEL);
        TextField name = new TextField(creating ? "" : user.getDisplayName());
        TextField username = new TextField(creating ? "" : user.getUsername());
        TextField email = new TextField(creating ? "" : user.getEmail());
        TextField address = new TextField(creating ? "" : user.getAddress());
        DatePicker dob = new DatePicker(creating ? null : user.getDateOfBirth());
        ChoiceBox<String> gender = new ChoiceBox<>();
        gender.getItems().setAll("Not specified", "Male", "Female", "Other");
        gender.setValue(creating ? "Not specified" : user.getGender());
        Label feedback = new Label(); feedback.setWrapText(true); feedback.setStyle("-fx-text-fill: #EC657B;");
        GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(11); grid.setPadding(new Insets(15));
        String[] captions = {"Full name", "Username", "Email", "Address", "Date of birth", "Gender"};
        Control[] fields = {name, username, email, address, dob, gender};
        for (int i = 0; i < fields.length; i++) {
            grid.add(new Label(captions[i]),0,i); grid.add(fields[i],1,i);
            fields[i].setPrefWidth(300);
        }
        grid.add(feedback,0,6,2,1);dialog.getDialogPane().setContent(grid);
        UserAccount[] saved = new UserAccount[1];
        dialog.setResultConverter(button -> button == save ? saved[0] : null);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            var result = creating ? service.createUser(name.getText(),username.getText(),email.getText(),address.getText(),dob.getValue(),gender.getValue())
                    : service.updateUser(user.getId(),name.getText(),username.getText(),email.getText(),address.getText(),dob.getValue(),gender.getValue());
            if (!result.isSuccess()) { feedback.setText(result.errors().get(0).message()); event.consume(); }
            else saved[0] = result.value().orElseThrow();
        });
        return dialog.showAndWait();
    }
    public static boolean showPassword(AdminService service, UserAccount user) {
        Dialog<Boolean> dialog = new Dialog<>(); style(dialog); dialog.setTitle("Update password for @"+user.getUsername());
        ButtonType save = new ButtonType("Update password",ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(save,ButtonType.CANCEL);
        PasswordField password = new PasswordField(), repeat = new PasswordField();
        Label feedback = new Label(); feedback.setStyle("-fx-text-fill: #EC657B;");
        VBox form = new VBox(10, new Label("New password (min. 8 characters)"), password,
                new Label("Confirm new password"), repeat, feedback);
        form.setPadding(new Insets(16)); form.setPrefWidth(360); dialog.getDialogPane().setContent(form);
        boolean[] updated = new boolean[1];
        dialog.setResultConverter(button -> button == save ? updated[0] : false);
        ((Button)dialog.getDialogPane().lookupButton(save)).addEventFilter(javafx.event.ActionEvent.ACTION,e -> {
            if (!password.getText().equals(repeat.getText())) {feedback.setText("Passwords do not match");e.consume();return;}
            var result = service.setUserPassword(user.getId(), password.getText());
            if (!result.isSuccess()) {feedback.setText(result.errors().get(0).message());e.consume();return;}
            updated[0] = true;
        });
        return dialog.showAndWait().orElse(false);
    }
}
