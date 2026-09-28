package app.overbay.orbit;

import android.os.Bundle;
import android.webkit.CookieManager;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (bridge != null && bridge.getWebView() != null) {
            cookieManager.setAcceptThirdPartyCookies(bridge.getWebView(), true);
        }
    }
}
