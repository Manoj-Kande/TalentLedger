const API_URL = process.env.NEXT_PUBLIC_API_URL || "";

const isDev = process.env.NODE_ENV === "development";

function trace(label: string, data?: unknown) {
  if (!isDev) return;
  if (data !== undefined) {
    console.debug(`[api] ${label}`, data);
    return;
  }
  console.debug(`[api] ${label}`);
}

class ApiClient {
  private baseUrl: string;

  constructor() {
    this.baseUrl = API_URL;
  }

  private shouldSendSessionHeader(path: string): boolean {
    return (
      path !== "/api/v1/auth/login" &&
      path !== "/api/v1/auth/register" &&
      path !== "/api/v1/auth/setup" &&
      path !== "/api/v1/auth/password-reset" &&
      path !== "/api/v1/auth/password-reset/confirm"
    );
  }

  private getHeaders(path: string): HeadersInit {
    const headers: HeadersInit = {
      "Content-Type": "application/json",
    };

    if (typeof window !== "undefined") {
      if (this.shouldSendSessionHeader(path)) {
        const sessionToken = localStorage.getItem("session_token");
        if (sessionToken) {
          headers["X-Session-Token"] = sessionToken;
        }
      }
      // Dev bypass
      const devUserId = localStorage.getItem("dev_user_id");
      if (
        devUserId &&
        process.env.NODE_ENV === "development" &&
        this.shouldSendSessionHeader(path)
      ) {
        headers["X-Dev-UserId"] = devUserId;
      }
    }

    return headers;
  }

  private clearAuthAndRedirect() {
    if (typeof window !== "undefined") {
      localStorage.removeItem("session_token");
      localStorage.removeItem("user");
      localStorage.removeItem("dev_user_id");
      window.location.replace("/login");
    }
  }

  private async request<T>(
    path: string,
    options: RequestInit = {},
  ): Promise<T> {
    const url = `${this.baseUrl}${path}`;
    const method = (options.method || "GET").toUpperCase();
    const config: RequestInit = {
      ...options,
      headers: {
        ...this.getHeaders(path),
        ...options.headers,
      },
    };

    trace("request:start", {
      method,
      path,
      url,
      hasSessionToken:
        typeof window !== "undefined" &&
        !!localStorage.getItem("session_token"),
    });

    let response: Response;
    try {
      response = await fetch(url, config);
    } catch (error) {
      trace("request:network-error", {
        method,
        path,
        url,
        error: error instanceof Error ? error.message : String(error),
      });
      throw error;
    }

    trace("request:response", {
      method,
      path,
      url,
      status: response.status,
      ok: response.ok,
    });

    if (!response.ok) {
      // 401s from the auth endpoints themselves (bad password, MFA required, etc.) are not
      // an expired session — there's no session yet. Let those fall through to the normal
      // error parsing below so the real server message is shown.
      const isAuthEndpoint = path.startsWith("/api/v1/auth");

      if (response.status === 401 && !isAuthEndpoint) {
        trace("request:unauthorized", {
          method,
          path,
          url,
          status: response.status,
        });
        this.clearAuthAndRedirect();
        throw new ApiError(
          "Session expired. Please sign in again.",
          "UNAUTHORIZED",
          401,
        );
      }

      const errorBody = await response.json().catch(() => null);
      if (errorBody && errorBody.error) {
        trace("request:api-error", {
          method,
          path,
          url,
          status: response.status,
          code: errorBody.error.code,
          message: errorBody.error.message,
        });
        throw new ApiError(
          errorBody.error.message || "An error occurred",
          errorBody.error.code || "UNKNOWN_ERROR",
          response.status,
        );
      }
      trace("request:http-error", {
        method,
        path,
        url,
        status: response.status,
      });
      throw new ApiError(
        `Request failed with status ${response.status}`,
        "HTTP_ERROR",
        response.status,
      );
    }

    const text = await response.text();
    trace("request:body", { method, path, url, hasBody: !!text });
    if (!text) return undefined as T;

    let data: any;
    try {
      data = JSON.parse(text);
    } catch (error) {
      trace("request:parse-error", {
        method,
        path,
        url,
        error: error instanceof Error ? error.message : String(error),
        bodyPreview: text.slice(0, 300),
      });
      throw error;
    }

    if (data.success === false && data.error) {
      trace("request:envelope-error", {
        method,
        path,
        url,
        code: data.error.code,
        message: data.error.message,
      });
      throw new ApiError(data.error.message, data.error.code, response.status);
    }

    trace("request:success", { method, path, url });

    return data.data !== undefined ? data.data : data;
  }

  async get<T>(
    path: string,
    params?: Record<string, string | number | boolean | undefined>,
  ): Promise<T> {
    let url = path;
    if (params) {
      const searchParams = new URLSearchParams();
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== "") {
          searchParams.append(key, String(value));
        }
      });
      const qs = searchParams.toString();
      if (qs) url += `?${qs}`;
    }
    return this.request<T>(url, { method: "GET" });
  }

  async post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>(path, {
      method: "POST",
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  async put<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>(path, {
      method: "PUT",
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  async patch<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>(path, {
      method: "PATCH",
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  async delete<T>(path: string): Promise<T> {
    return this.request<T>(path, { method: "DELETE" });
  }

  /**
   * Upload a file using multipart/form-data.
   * Used for dump CSV uploads.
   */
  async upload<T>(
    path: string,
    file: File,
    fieldName: string = "file",
  ): Promise<T> {
    const url = `${this.baseUrl}${path}`;
    const formData = new FormData();
    formData.append(fieldName, file);

    const headers: HeadersInit = {};
    if (typeof window !== "undefined") {
      const sessionToken = localStorage.getItem("session_token");
      if (sessionToken) headers["X-Session-Token"] = sessionToken;
      const devUserId = localStorage.getItem("dev_user_id");
      if (devUserId && process.env.NODE_ENV === "development") {
        headers["X-Dev-UserId"] = devUserId;
      }
    }

    const response = await fetch(url, {
      method: "POST",
      headers,
      body: formData,
    });

    if (!response.ok) {
      // Handle 401 on uploads too
      if (response.status === 401) {
        this.clearAuthAndRedirect();
        throw new ApiError(
          "Session expired. Please sign in again.",
          "UNAUTHORIZED",
          401,
        );
      }

      const errorBody = await response.json().catch(() => null);
      if (errorBody && errorBody.error) {
        throw new ApiError(
          errorBody.error.message,
          errorBody.error.code,
          response.status,
        );
      }
      throw new ApiError(
        `Upload failed with status ${response.status}`,
        "HTTP_ERROR",
        response.status,
      );
    }

    const data = await response.json();
    return data.data !== undefined ? data.data : data;
  }

  /**
   * Subscribe to SSE progress events for dump upload.
   */
  getDumpProgressUrl(dumpId: string): string {
    const token =
      typeof window !== "undefined"
        ? localStorage.getItem("session_token")
        : "";
    return `${this.baseUrl}/api/v1/dumps/${dumpId}/progress?token=${token}`;
  }
}

export class ApiError extends Error {
  code: string;
  status: number;

  constructor(message: string, code: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
  }
}

export const apiClient = new ApiClient();
export default apiClient;
