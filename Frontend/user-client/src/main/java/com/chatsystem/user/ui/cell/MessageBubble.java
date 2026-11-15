package com.chatsystem.user.ui.cell;
import com.chatsystem.shared.model.Message;
import javafx.scene.control.ListCell;
public final class MessageBubble extends ListCell<Message>{
    @Override protected void updateItem(Message item,boolean empty){super.updateItem(item,empty);setText(empty||item==null?null:item.getContent());getStyleClass().removeAll("message-bubble");if(!empty&&item!=null)getStyleClass().add("message-bubble");}
}
