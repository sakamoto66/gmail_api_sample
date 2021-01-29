import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;

import com.google.api.services.gmail.model.Message;

public class App {

    public static void main(String... args) throws GeneralSecurityException, IOException {
        final String query = String.format("newer_than:1d to:%s %s", args[0], "");
        LocalDateTime current = LocalDateTime.now();//LocalDateTime.of(2020,8,3,15,20,10);
        int retryCount = 6;

        Message msg = GmailUtil.receiveNewEmail(query, current, retryCount);

        System.out.println("[Subject]");
        System.out.println(GmailUtil.getSubject(msg));

        System.out.println("[Body]");
        System.out.println(GmailUtil.getBody(msg, "UTF-8"));

        System.out.println("[Headers]");
        msg.getPayload().getHeaders().forEach( header -> {
            System.out.println("  ["+header.getName()+"]");
            System.out.println("    "+header.getValue());
        });
        
    }
}
