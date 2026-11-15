package com.chatsystem.admin.navigation;

import com.chatsystem.admin.*; import com.chatsystem.admin.controller.AdminShellController; import com.chatsystem.admin.ui.FxmlViewLoader;
import javafx.beans.property.*; import javafx.scene.*; import javafx.scene.control.Label; import javafx.scene.layout.StackPane; import javafx.stage.Stage; import java.util.*;
public final class AdminNavigator {
    private final Stage stage; private final FxmlViewLoader views; private final ReadOnlyObjectWrapper<ViewRequest> current=new ReadOnlyObjectWrapper<>(); private Parent shell; private AdminShellController shellController;
    public AdminNavigator(Stage stage,AdminApplicationContext context){this.stage=Objects.requireNonNull(stage);views=new FxmlViewLoader(Objects.requireNonNull(context),this);}
    public void start(){navigate(ViewRequest.of(AdminRoute.DASHBOARD));}
    public ViewRequest currentRequest(){return current.get();} public ReadOnlyObjectProperty<ViewRequest> currentRequestProperty(){return current.getReadOnlyProperty();}
    public void navigate(ViewRequest request){if(shell==null){var loaded=views.load("/fxml/shell/admin-shell.fxml");shell=loaded.root();shellController=(AdminShellController)loaded.controller();} Parent content=routeContent(request);shellController.show(request,content);if(stage.getScene()==null){Scene scene=new Scene(shell,1480,920);scene.getStylesheets().add(views.resource("/css/dark.css").toExternalForm());stage.setResizable(false);stage.setScene(scene);}current.set(request);}
    private Parent routeContent(ViewRequest request){Label label=new Label(request.route().title());label.getStyleClass().add("page-title");StackPane pane=new StackPane(label);pane.getStyleClass().add("app-root");pane.setUserData(request);return pane;}
}
