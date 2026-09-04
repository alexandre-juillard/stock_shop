package fr.stockshop.stock_api.security.oauth2;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class OAuth2AuthenticationFailureHandlerTest {

  private final OAuth2AuthenticationFailureHandler handler =
      new OAuth2AuthenticationFailureHandler("stockshop://oauth2/callback");

  @Test
  void redirectsToMobileDeepLinkWithErrorParamOnAuthenticationFailure() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    handler.onAuthenticationFailure(
        request,
        response,
        new OAuth2AuthenticationException(
            new OAuth2Error("authorization_request_not_found"), "état corrompu"));

    verify(response).sendRedirect("stockshop://oauth2/callback?error=oauth2_failed");
  }
}
