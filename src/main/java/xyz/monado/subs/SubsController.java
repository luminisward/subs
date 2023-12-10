package xyz.monado.subs;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.yaml.snakeyaml.Yaml;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static io.netty.util.AsciiString.containsIgnoreCase;

@Controller
public class SubsController {
    private final SpringTemplateEngine templateEngine;
    private final Yaml yaml;
    private final RequestUtil requestUtil;

    public SubsController(SpringTemplateEngine templateEngine, Yaml yaml, RequestUtil requestUtil) {
        this.templateEngine = templateEngine;
        this.yaml = yaml;
        this.requestUtil = requestUtil;
    }


    @GetMapping("/")
    public ResponseEntity<String> getConfig(Model model, @Valid QueryParams queryParams, @RequestHeader(HttpHeaders.USER_AGENT) String userAgent, HttpServletRequest request) {
        String fullUrl = getURL(request);
        model.addAttribute("url", fullUrl);

        String configUrl = queryParams.getUrl();
        UriComponents uriComponents = UriComponentsBuilder.fromUriString(configUrl).build(false);
        URI uri = uriComponents.toUri();
        ClientType clientType = queryParams.getClientType();

        if (clientType == null) {
            if (containsIgnoreCase(userAgent, "Clash")) {
                clientType = ClientType.CLASH;
            } else if (containsIgnoreCase(userAgent, "Surge") || containsIgnoreCase(userAgent, "Surfboard")) {
                clientType = ClientType.SURGE;
            } else {
                return ResponseEntity.badRequest().body("unknown client type");
            }
        }

        var remoteConfigResponseEntity = requestUtil.requestRemoteConfig(uri, clientType);
        if (remoteConfigResponseEntity == null) {
            return ResponseEntity.badRequest().body("remote config response is null");
        }

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();
        var responseHeaders = remoteConfigResponseEntity.getHeaders();
        var contentDisposition = responseHeaders.getContentDisposition();
        if (contentDisposition.getType() != null && contentDisposition.getFilename() != null) {
            var newFilename = "(. Y .) " + contentDisposition.getFilename();
            ContentDisposition newContentDisposition = ContentDisposition.builder(contentDisposition.getType()).filename(newFilename).build();
            responseBuilder.header(HttpHeaders.CONTENT_DISPOSITION, newContentDisposition.toString());
        }

        if (clientType == ClientType.CLASH) {
            return getClashResponseEntity(model, remoteConfigResponseEntity, responseBuilder);
        } else if (clientType == ClientType.SURGE) {
            return getSurgeResponseEntity(model, remoteConfigResponseEntity, responseBuilder);
        } else {
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity<String> getSurgeResponseEntity(Model model, ResponseEntity<String> remoteConfigResponseEntity, ResponseEntity.BodyBuilder responseBuilder) {
        String iniData = remoteConfigResponseEntity.getBody();
        assert iniData != null;
        var configMap = parseIniSection(iniData);


        var generalMap = parseIniSectionContent(configMap.get("General"));
        generalMap.keySet().retainAll(Arrays.asList("dns-server", "skip-proxy", "exclude-simple-hostnames", "ipv6", "internet-test-url", "proxy-test-url", "test-timeout"));
        configMap.put("General", convertIniSectionContentMapToString(generalMap));

        if (configMap.get("Panel") == null) {
            String proxy = configMap.get("Proxy");
            var proxyMap = parseIniSectionContent(proxy);
            var panelStringBuilder = new StringBuilder();
            panelStringBuilder.append("SubscribeInfo=title=SubscribeInfo, content=");
            var iterator = proxyMap.keySet().iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                if (containsIgnoreCase(key, "Bandwidth") || containsIgnoreCase(key, "Expire")) {
                    panelStringBuilder.append(key).append("\\n");
                    iterator.remove();
                }
                if (containsIgnoreCase(key, "Direct")) {
                    iterator.remove();
                }
            }
            panelStringBuilder.append(", style=info");
            configMap.put("Panel", panelStringBuilder.toString());
            configMap.put("Proxy", convertIniSectionContentMapToString(proxyMap));
        }

        LinkedHashMap<String, String> proxyMap = parseIniSectionContent(configMap.get("Proxy")).entrySet().stream().filter(e -> !e.getValue().isEmpty()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
        configMap.put("Proxy", convertIniSectionContentMapToString(proxyMap));
        List<String> proxyList = proxyMap.keySet().stream().toList();

        LinkedHashMap<String, String> proxyGroupMap = new LinkedHashMap<>();
        proxyGroupMap.put("PROXY", "select, AUTO, " + String.join(",", proxyList));
        proxyGroupMap.put("AUTO", "url-test, " + String.join(",", proxyList));
        proxyGroupMap.put("UNMATCHED", "select, PROXY, DIRECT");
        configMap.put("Proxy Group", convertIniSectionContentMapToString(proxyGroupMap));


        configMap.keySet().retainAll(Arrays.asList("General", "Proxy", "Proxy Group", "Host", "Panel"));

        var result = convertIniMapToString(configMap);
        model.addAttribute("result", result);
        Context context = new Context();
        context.setVariables(model.asMap());
        String body = templateEngine.process("surge", context);
        return responseBuilder.body(body);
    }

    private ResponseEntity<String> getClashResponseEntity(Model model, ResponseEntity<String> remoteConfigResponseEntity, ResponseEntity.BodyBuilder responseBuilder) {

        Map<String, Object> configYamlMap = yaml.load(remoteConfigResponseEntity.getBody());
        var proxies = (List<Map<String, Object>>) configYamlMap.get("proxies");
        Map<String, Object> proxiesAndGroupsAndDns = new HashMap<>();

        var proxiesNames = proxies.stream().map(p -> p.get("name")).toArray();
        List<Object> proxiesNamesWithSpecial = new ArrayList<>(Arrays.asList("URL-TEST", "FALLBACK"));
        proxiesNamesWithSpecial.addAll(Arrays.asList(proxiesNames));

        var groups = new ArrayList<Map<String, Object>>();
        groups.add(Map.of("name", "PROXY", "type", "select", "proxies", proxiesNamesWithSpecial));
        groups.add(Map.of("name", "URL-TEST", "type", "url-test", "proxies", proxiesNames, "url", "https://www.gstatic.com/generate_204", "interval", 300));
        groups.add(Map.of("name", "FALLBACK", "type", "fallback", "proxies", proxiesNames, "url", "https://www.gstatic.com/generate_204", "interval", 300));
        groups.add(Map.of("name", "FINAL", "type", "select", "proxies", new String[]{"PROXY", "URL-TEST", "FALLBACK", "DIRECT"}));

        proxiesAndGroupsAndDns.put("proxies", proxies);
        proxiesAndGroupsAndDns.put("proxy-groups", groups);

        model.addAttribute("proxiesAndGroups", yaml.dump(proxiesAndGroupsAndDns));
        Context context = new Context();
        context.setVariables(model.asMap());
        String body = templateEngine.process("clash", context);
        String subscriptionUserinfo = "subscription-userinfo";

        var responseHeaders = remoteConfigResponseEntity.getHeaders();
        responseBuilder.header(subscriptionUserinfo, responseHeaders.getFirst(subscriptionUserinfo));

        return responseBuilder.body(body);
    }

    private static LinkedHashMap<String, String> parseIniSection(String iniString) {
        List<String> lines = Arrays.stream(iniString.split("\n"))
                .filter(line -> !(line.startsWith("#") || line.startsWith("//"))).toList();
        LinkedHashMap<String, StringBuilder> iniMap = new LinkedHashMap<>();
        String currentSection = null;
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length() - 1);
                iniMap.put(currentSection, new StringBuilder());
            } else if (currentSection != null) {
                iniMap.get(currentSection).append(line).append("\n");
            }
        }
        return iniMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString(), (a, b) -> b, LinkedHashMap::new));
    }

    private static String convertIniMapToString(LinkedHashMap<String, String> iniMap) {
        StringBuilder builder = new StringBuilder();

        for (Map.Entry<String, String> entry : iniMap.entrySet()) {
            builder.append("[").append(entry.getKey()).append("]\n");
            builder.append(entry.getValue());
        }

        return builder.toString();
    }

    private static LinkedHashMap<String, String> parseIniSectionContent(String sectionContent) {
        LinkedHashMap<String, String> iniMap = new LinkedHashMap<>();
        String[] lines = sectionContent.split("\n");
        for (String line : lines) {
            String[] keyValue = line.split("=", 2);
            if (keyValue.length == 2) {
                iniMap.put(keyValue[0].trim(), keyValue[1].trim());
            } else {
                iniMap.put(keyValue[0].trim(), "");
            }
        }
        return iniMap;
    }

    private static String convertIniSectionContentMapToString(LinkedHashMap<String, String> iniMap) {
        StringBuilder builder = new StringBuilder();

        for (Map.Entry<String, String> entry : iniMap.entrySet()) {
            builder.append(entry.getKey());
            builder.append("=");
            builder.append(entry.getValue());
            builder.append("\n");
        }

        return builder.toString();
    }

    public String getURL(HttpServletRequest request) {
        StringBuilder url = new StringBuilder();

        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null) {
//            scheme = request.getScheme();
            scheme = "https";
        }

        url.append(scheme);
        url.append("://");

        String host = request.getHeader("X-Forwarded-Host");
        if (host == null) {
            host = request.getServerName() + ":" + request.getServerPort();
        }

        url.append(host);
        url.append(request.getRequestURI());

        if (request.getQueryString() != null) {
            url.append('?');
            url.append(request.getQueryString());
        }

        return url.toString();
    }
}