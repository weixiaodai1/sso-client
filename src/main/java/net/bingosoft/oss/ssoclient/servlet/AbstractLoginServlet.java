package net.bingosoft.oss.ssoclient.servlet;

import net.bingosoft.oss.ssoclient.SSOClient;
import net.bingosoft.oss.ssoclient.internal.Strings;
import net.bingosoft.oss.ssoclient.internal.Urls;
import net.bingosoft.oss.ssoclient.model.AccessToken;
import net.bingosoft.oss.ssoclient.model.Authentication;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;

public abstract class AbstractLoginServlet extends HttpServlet{
    
    protected static final String ID_TOKEN_PARAM                 = "id_token";
    protected static final String AUTHZ_CODE_PARAM               = "code";
    
    private SSOClient client;
    
    @Override
    public void init(ServletConfig config) throws ServletException {
        this.client = getClient(config);
        super.init(config);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if(isRedirectedFromSSO(req)){
            gotoLocalLogin(req,resp);
        }else {
            redirectToSSOLogin(req,resp);
        }
    }

    protected void redirectToSSOLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String redirectUri = buildRedirectUri(req,resp);
        String loginUrl = buildLoginUrl(req,resp,redirectUri);
        
        resp.sendRedirect(loginUrl);
        
    }

    protected void gotoLocalLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String state = req.getParameter("state");
        if(!req.getSession().getId().equals(state)){
            resp.sendError(HttpURLConnection.HTTP_BAD_REQUEST,"state has been change!");
            return;
        }

        String idToken = req.getParameter(ID_TOKEN_PARAM);
        String code = req.getParameter(AUTHZ_CODE_PARAM);
        
        Authentication authc = client.verifyIdToken(idToken);
        AccessToken token = client.obtainAccessTokenByCode(code);

        localLogin(req,resp,authc,token);

        String returnUrl = req.getParameter("return_url");
        if(Strings.isEmpty(returnUrl)){
            returnUrl = Urls.getServerBaseUrl(req);
        }
        resp.sendRedirect(returnUrl);
    }
    
    protected String buildLoginUrl(HttpServletRequest req, HttpServletResponse resp, String redirectUri) {
        String authzEndpoint = client.getConfig().getAuthorizationEndpointUrl();
        authzEndpoint = Urls.appendQueryString(authzEndpoint,"response_type","code id_token");
        authzEndpoint = Urls.appendQueryString(authzEndpoint,"client_id",client.getConfig().getClientId());
        authzEndpoint = Urls.appendQueryString(authzEndpoint,"redirect_uri",redirectUri);
        authzEndpoint = Urls.appendQueryString(authzEndpoint,"state",req.getSession().getId());
        return authzEndpoint;
    }
    
    /**
     * 构造SSO登录完成后的回调url，一般情况下，在注册SSO应用的时候，需要保证这个uri可以通过SSO的验证。
     * 这个方法构造的url一般是如下格式：
     * 
     * <pre>
     *     http(s)://${domain}:${port}/${contextPath}/ssoclient?${queryString}
     *     示例：
     *     http://www.example.com:80/demo/ssoclient?name=admin
     * </pre>
     * 
     * 一般情况下要求注册client的时候，填写的回调地址(redirect_uri)必须能够验证这里构造的url实现自动完成登录的过程。
     * 
     * 如果由于其他原因，回调地址不能设置为匹配这个地址的表达式，请重写这个方法，并自己处理登录完成后的回调请求。
     */
    protected String buildRedirectUri(HttpServletRequest req, HttpServletResponse resp){
        String contextPath = Urls.getServerBaseUrl(req);
        String current = contextPath + req.getRequestURI();
        String queryString = req.getQueryString();
        if(Strings.isEmpty(queryString)){
            return current;
        }else {
            return current+"?"+queryString;
        }
    }

    protected boolean isRedirectedFromSSO(HttpServletRequest req){
        String idToken = req.getParameter(ID_TOKEN_PARAM);
        String accessToken = req.getParameter(AUTHZ_CODE_PARAM);
        return !Strings.isEmpty(idToken) && !Strings.isEmpty(accessToken);
    }
    
    
    
    /**
     * 返回一个{@link SSOClient}对象
     */
    protected abstract SSOClient getClient(ServletConfig config) throws ServletException ;

    /**
     * 用户在SSO登录成功后，进行本地登录
     */
    protected abstract void localLogin(HttpServletRequest req, HttpServletResponse resp, Authentication authc, AccessToken token) throws ServletException, IOException ;
}