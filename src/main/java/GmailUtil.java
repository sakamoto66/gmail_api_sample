import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeoutException;

import com.google.api.client.util.Base64;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;

public class GmailUtil {
    /**
     * 新しいメールを受信する
     * 
     * @param query      検索クエリー(https://support.google.com/mail/answer/7190)
     * @param newer      指定された時刻より新しいメールを対象にする
     * @param retryCount リトライ回数(10秒間隔)
     * @return 受信メール
     */
    public static Message receiveNewEmail(final String query, LocalDateTime newer, int retryCount) {
        try {
            while (true) {
                Message msg = receiveNewEmail(query);
                if (null != msg && newer.isBefore(getDateTime(msg))) {
                    return msg;
                }
                if (0 > --retryCount) {
                    break;
                }
                Thread.sleep(10000);
            }
            throw new TimeoutException("faild receive new email");
        } catch (InterruptedException | IOException | TimeoutException e) {
            throw new RuntimeException("faild receive new email", e);
        }
    }

    private static Message receiveNewEmail(final String query) throws IOException {
        ListMessagesResponse messagesResponse = GoogleService.factory().gmail().users().messages().list("me")
                .setMaxResults(1L).setQ(query).execute();
        List<Message> messageList = messagesResponse.getMessages();
        if (null == messageList || 0 == messageList.size()) {
            return null;
        }
        Message msg = messageList.get(0);
        return GoogleService.factory().gmail().users().messages().get("me", msg.getId()).execute();
    }

    /**
     * メールの送信日時を取得
     * @param msg
     * @return 送信日時
     */
    public static LocalDateTime getDateTime(Message msg) {
        String val = msg.getPayload().getHeaders().stream().filter(h -> h.getName().equals("Date")).findFirst().get()
                .getValue();
        return LocalDateTime.parse(val, DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    /**
     * メールのタイトルを取得
     * @param msg 受信メール
     * @return タイトル
     */
    public static String getSubject(Message msg) {
        String val = msg.getPayload().getHeaders().stream().filter(h -> h.getName().equals("Subject")).findFirst().get()
                .getValue();
        return val;
    }

    /**
     * メールの本文を取得
     * @param msg 受信メール
     * @param encode
     * @return 本文
     */
    public static String getBody(Message msg, String encode) {
        // 本文の取得(Base64)
        String base64 = getBodyBase64(msg.getPayload());
        // Base64からデコード(byte列)
        byte[] buff = Base64.decodeBase64(base64);
        // byte列からStringに変換
        try {
            return new String(buff, encode);
        } catch (UnsupportedEncodingException e) {
            return base64;
        }
    }

    private static String getBodyBase64(MessagePart part) {
        if(null == part) {
            return null;
        }
        MessagePartBody body = part.getBody();
        if(null != body) {
            Object data = body.get("data");
            if(null != data) {
                return data.toString();
            }
        }

        List<MessagePart> parts = part.getParts();
        if(null == parts) {
            return null;
        }
        return getBodyBase64(parts.get(0));
    }
}
