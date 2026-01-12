// Appwrite Function proxy template (Node.js 18)
// Purpose: forward validated requests to external APIs without exposing keys to clients.
// Security notes:
// - Requires authenticated user/session (checks x-appwrite-user-id).
// - Validates project header.
// - Validates payload shape.
// - Uses env var OPENAI_API_KEY (or other) for outbound call.
// - Never logs secrets or full responses.

export default async ({ req, res, log, error }) => {
  try {
    const projectId = req.headers["x-appwrite-project"];
    if (projectId !== "696459cc001abc691815") {
      return res.json({ error: "unauthorized_project" }, 401);
    }

    const userId = req.headers["x-appwrite-user-id"];
    if (!userId) {
      return res.json({ error: "auth_required" }, 401);
    }

    // Basic rate limit by user (soft, per-execution memory). For production use Redis or Appwrite Ratelimiter.
    if (!globalThis.rateLimiter) globalThis.rateLimiter = new Map();
    const now = Date.now();
    const windowMs = 60_000;
    const limit = 30;
    const key = userId;
    const bucket = globalThis.rateLimiter.get(key) || [];
    const recent = bucket.filter((t) => now - t < windowMs);
    if (recent.length >= limit) {
      return res.json({ error: "rate_limited" }, 429);
    }
    recent.push(now);
    globalThis.rateLimiter.set(key, recent);

    const body = req.body ? JSON.parse(req.body) : {};
    // Minimal schema validation
    if (typeof body.prompt !== "string" || body.prompt.length === 0 || body.prompt.length > 4000) {
      return res.json({ error: "invalid_payload" }, 400);
    }

    const apiKey = process.env.OPENAI_API_KEY;
    if (!apiKey) {
      return res.json({ error: "missing_backend_key" }, 500);
    }

    // Forward to external API (example: OpenAI chat completions). Replace with your target API as needed.
    const response = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        model: "gpt-4o-mini",
        messages: [{ role: "user", content: body.prompt }],
        max_tokens: 128,
      }),
    });

    if (!response.ok) {
      return res.json({ error: "upstream_error", status: response.status }, 502);
    }

    const json = await response.json();
    const minimal = {
      reply: json?.choices?.[0]?.message?.content ?? "",
      usage: json?.usage ?? null,
    };

    return res.json(minimal, 200);
  } catch (e) {
    error(String(e));
    return res.json({ error: "internal_error" }, 500);
  }
};
