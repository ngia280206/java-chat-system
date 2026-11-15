package com.chatsystem.admin.ui;

import com.chatsystem.admin.*; import com.chatsystem.admin.controller.AdminShellController; import com.chatsystem.admin.navigation.AdminNavigator;
import com.chatsystem.admin.controller.*;
import javafx.fxml.FXMLLoader; import javafx.scene.Parent; import java.io.IOException; import java.net.URL; import java.util.*;
public final class FxmlViewLoader {
    public record LoadedView(Parent root,Object controller){}
    private final AdminApplicationContext context; private final AdminNavigator navigator;
    public FxmlViewLoader(AdminApplicationContext context,AdminNavigator navigator){this.context=Objects.requireNonNull(context);this.navigator=Objects.requireNonNull(navigator);}
    public URL resource(String path){URL url=FxmlViewLoader.class.getResource(path);if(url==null)throw new IllegalArgumentException("Missing classpath resource: "+path);return url;}
    public LoadedView load(String path){FXMLLoader loader=new FXMLLoader(resource(path));loader.setControllerFactory(this::createController);try{return new LoadedView(loader.load(),loader.getController());}catch(IOException e){throw new IllegalStateException("Could not load "+path,e);}}
    public Object createController(Class<?> type){
        if(type==AdminShellController.class)return new AdminShellController(navigator);
        if(type==DashboardController.class)return new DashboardController(context.admin(),navigator);
        if(type==UsersController.class)return new UsersController(context.admin(),navigator);
        if(type==UserDetailsController.class)return new UserDetailsController(context.admin(),navigator);
        if(type==GroupsController.class)return new GroupsController(context.admin(),navigator);
        if(type==GroupDetailsController.class)return new GroupDetailsController(context.admin(),navigator);
        throw new IllegalArgumentException("No explicit controller constructor for "+type.getName());}
}
