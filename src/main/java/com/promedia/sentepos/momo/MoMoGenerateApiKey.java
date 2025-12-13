import java.io.*;
import java.net.*;
import java.util.Base64;

public class MoMoGenerateApiKey {

    private static final String USER_ID = "b4a4c5a8-0d57-4b18-b056-5e6f1c3da7c9";
    private static final String SUB_KEY = "9dc12346168343468b0ef45536ba2953";

    public static void main(String[] args) throws Exception {
        URL url = new URL("https://sandbox.momodeveloper.mtn.com/v1_0/apiuser/" 
                + USER_ID + "/apikey");

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Ocp-Apim-Subscription-Key", SUB_KEY);
        conn.setDoOutput(true);

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300)
                ? conn.getInputStream() : conn.getErrorStream();

        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line, response = "";
        while ((line = br.readLine()) != null)
            response += line;

        System.out.println("API KEY RESPONSE: " + response);
    }
}