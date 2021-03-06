package com.vlkan.hrrs.servlet;

import com.vlkan.hrrs.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public abstract class HrrsFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HrrsFilter.class);

    public static final String SERVLET_CONTEXT_ATTRIBUTE_KEY = HrrsFilter.class.getCanonicalName();

    public static final long DEFAULT_MAX_RECORDABLE_PAYLOAD_BYTE_COUNT = 10 * 1024 * 1024;

    private final HrrsIdGenerator idGenerator = new HrrsIdGenerator(4);

    private volatile boolean enabled = true;

    private ServletContext servletContext = null;

    public HrrsFilter() {
        // Do nothing.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (isRequestRecordable(request)) {
            ByteArrayOutputStream requestOutputStream = new ByteArrayOutputStream();
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            TeeServletInputStream teeServletInputStream = new TeeServletInputStream(
                    httpRequest.getInputStream(),
                    requestOutputStream,
                    getMaxRecordablePayloadByteCount());
            HttpServletRequest teeRequest = new HrrsHttpServletRequestWrapper(httpRequest, teeServletInputStream);
            chain.doFilter(teeRequest, response);
            long totalPayloadByteCount = teeServletInputStream.getByteCount();
            byte[] recordedPayloadBytes = requestOutputStream.toByteArray();
            HttpRequestRecord record = createRecord(httpRequest, recordedPayloadBytes, totalPayloadByteCount);
            HttpRequestRecord filteredRecord = filterRecord(record);
            if (filteredRecord != null) {
                getWriter().write(record);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    private boolean isRequestRecordable(ServletRequest request) {
        return enabled && request instanceof HttpServletRequest && isRequestRecordable((HttpServletRequest) request);
    }

    /**
     * Checks if the given HTTP request is recordable.
     */
    protected boolean isRequestRecordable(HttpServletRequest request) {
        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.trace("switched state (enabled={})", enabled);
    }

    private HttpRequestRecord createRecord(HttpServletRequest request, byte[] recordedPayloadBytes, long totalPayloadByteCount) {
        String id = createRequestId(request);
        Date timestamp = new Date();
        String groupName = createRequestGroupName(request);
        String uri = createRequestUri(request);
        HttpRequestMethod method = HttpRequestMethod.valueOf(request.getMethod());
        List<HttpRequestHeader> headers = createHeaders(request);
        HttpRequestPayload payload = createPayload(recordedPayloadBytes, totalPayloadByteCount);
        return ImmutableHttpRequestRecord
                .builder()
                .id(id)
                .timestamp(timestamp)
                .groupName(groupName)
                .uri(uri)
                .method(method)
                .headers(headers)
                .payload(payload)
                .build();
    }

    protected static String createRequestUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        boolean blankQueryString = queryString == null || queryString.matches("^\\s*$");
        return blankQueryString ? uri : String.format("%s?%s", uri, queryString);
    }

    private List<HttpRequestHeader> createHeaders(HttpServletRequest request) {
        List<HttpRequestHeader> headers = Collections.emptyList();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String value = request.getHeader(name);
            ImmutableHttpRequestHeader header = ImmutableHttpRequestHeader
                    .builder()
                    .name(name)
                    .value(value)
                    .build();
            if (headers.isEmpty()) {
                headers = new ArrayList<HttpRequestHeader>();
            }
            headers.add(header);
        }
        return headers;
    }

    private HttpRequestPayload createPayload(byte[] recordedPayloadBytes, long totalPayloadByteCount) {
        long missingByteCount = totalPayloadByteCount - recordedPayloadBytes.length;
        return ImmutableHttpRequestPayload
                .builder()
                .missingByteCount(missingByteCount)
                .bytes(recordedPayloadBytes)
                .build();
    }

    /**
     * Maximum amount of bytes that can be recorded per request.
     * Defaults to {@link HrrsFilter#DEFAULT_MAX_RECORDABLE_PAYLOAD_BYTE_COUNT}.
     */
    public long getMaxRecordablePayloadByteCount() {
        return DEFAULT_MAX_RECORDABLE_PAYLOAD_BYTE_COUNT;
    }

    /**
     * Create a group name for the given request.
     *
     * Group names are used to group requests and later on are used
     * as identifiers while reporting statistics in the replayer.
     * It is strongly recommended to use group names similar to Java
     * package names.
     */
    protected String createRequestGroupName(HttpServletRequest request) {
        String requestUri = createRequestUri(request);
        return requestUri
                .replaceFirst("\\?.*", "")      // Replace query parameters.
                .replaceFirst("^/", "")         // Replace the initial slash.
                .replaceAll("/", ".");          // Replace all slashes with dots.
    }

    /**
     * Creates a unique identifier for the given request.
     */
    protected String createRequestId(HttpServletRequest request) {
        return idGenerator.next();
    }

    /**
     * Filter the given record prior to writing.
     * @return the modified record or null to exclude the record
     */
    protected HttpRequestRecord filterRecord(HttpRequestRecord record) {
        return record;
    }

    abstract protected HttpRequestRecordWriter getWriter();

    @Override
    public synchronized void init(FilterConfig filterConfig) throws ServletException {
        checkArgument(servletContext == null, "servlet context is already initialized");
        servletContext = filterConfig.getServletContext();
        Object prevAttribute = servletContext.getAttribute(SERVLET_CONTEXT_ATTRIBUTE_KEY);
        checkArgument(prevAttribute == null, "servlet context attribute is already initialized");
        servletContext.setAttribute(SERVLET_CONTEXT_ATTRIBUTE_KEY, this);
        LOGGER.trace("initialized");
    }

    @Override
    public synchronized void destroy() {
        checkNotNull(servletContext, "servlet context is not initialized");
        servletContext.removeAttribute(SERVLET_CONTEXT_ATTRIBUTE_KEY);
        LOGGER.trace("destroyed");
    }

}
