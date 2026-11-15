package com.chatsystem.admin.ui;

import javafx.scene.control.*; import javafx.scene.layout.Pane;
public final class DisabledControlSupport {
    private DisabledControlSupport(){}
    public static Tooltip install(Control control, Pane wrapper, String reason){
        if(!wrapper.getChildren().contains(control)) throw new IllegalArgumentException("Disabled control must be a child of its tooltip wrapper");
        if(reason==null||reason.isBlank()) throw new IllegalArgumentException("A disabled reason is required");
        control.setDisable(true); control.setMouseTransparent(true); wrapper.setPickOnBounds(true); wrapper.setAccessibleText(reason);
        Tooltip tooltip=new Tooltip(reason); Tooltip.install(wrapper,tooltip); return tooltip;
    }
}
