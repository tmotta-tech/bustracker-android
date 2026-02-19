/**
 * BUS TRACKER PROXY v6 - SIMPLE MODE (No KV, No Cron)
 * 
 * Usa o cache automático da Cloudflare (edge cache).
 * - Primeira request: busca da API (~15s)
 * - Próximas 30s: responde do cache instantâneo
 * - Após 30s: busca novamente
 */

const RIO_API_URL = "http://dados.mobilidade.rio/gps/sppo";
// Cache 60s (API demora ~90s para responder 50MB, então cache longo evita timeout)
const CACHE_SECONDS = 60;

export default {
    async fetch(request, env, ctx) {
        const url = new URL(request.url);
        const linhaParam = url.searchParams.get("linha");

        const corsHeaders = {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, OPTIONS",
            "Content-Type": "application/json"
        };

        if (request.method === "OPTIONS") {
            return new Response(null, { headers: corsHeaders });
        }

        if (!linhaParam) {
            return new Response(JSON.stringify({
                msg: "API Online V6 - Simple Mode",
                usage: "?linha=679 ou ?linha=343,485",
                cache: CACHE_SECONDS + "s"
            }), { headers: corsHeaders });
        }

        try {
            // Usar cache da Cloudflare (automático, 30 segundos)
            const cache = caches.default;
            const cacheKey = new Request(RIO_API_URL + "?cached=true");

            // Tenta pegar do cache primeiro
            let allBuses = null;
            let cacheHit = false;

            const cachedResponse = await cache.match(cacheKey);
            if (cachedResponse) {
                allBuses = await cachedResponse.json();
                cacheHit = true;
            }

            // Se não tem cache, busca da API com RETRY
            if (!allBuses) {
                console.log("Cache miss, buscando da API...");

                // Retry logic: 3 tentativas com backoff exponencial
                var attempts = 0;
                var maxAttempts = 3;
                var apiResponse = null;

                while (attempts < maxAttempts) {
                    attempts++;
                    try {
                        apiResponse = await fetch(RIO_API_URL);
                        if (apiResponse.ok) {
                            break; // Sucesso, sai do loop
                        }

                        console.log("Tentativa " + attempts + " falhou: " + apiResponse.status);

                        if (attempts < maxAttempts && (apiResponse.status === 503 || apiResponse.status === 502)) {
                            // Espera antes de tentar novamente: 1s, 2s, 4s
                            var delay = Math.pow(2, attempts - 1) * 1000;
                            await new Promise(function (resolve) { setTimeout(resolve, delay); });
                        }
                    } catch (fetchError) {
                        console.log("Tentativa " + attempts + " exception: " + fetchError.message);
                        if (attempts < maxAttempts) {
                            var delay = Math.pow(2, attempts - 1) * 1000;
                            await new Promise(function (resolve) { setTimeout(resolve, delay); });
                        }
                    }
                }

                if (!apiResponse || !apiResponse.ok) {
                    throw new Error("API Prefeitura erro após " + maxAttempts + " tentativas");
                }

                allBuses = await apiResponse.json();

                // Salva no cache por 30 segundos
                const toCache = new Response(JSON.stringify(allBuses), {
                    headers: {
                        "Content-Type": "application/json",
                        "Cache-Control": "s-maxage=" + CACHE_SECONDS
                    }
                });
                ctx.waitUntil(cache.put(cacheKey, toCache));
            }

            // Parse das linhas solicitadas
            const requestedLines = linhaParam.split(",").map(function (l) {
                return l.trim().replace(".0", "");
            });

            // FILTRAGEM EXATA
            const filtered = allBuses.filter(function (bus) {
                var busLine = (bus.linha || "").toString().replace(".0", "").trim();

                return requestedLines.some(function (target) {
                    // Comparação EXATA
                    if (busLine === target) return true;

                    // Suporta sufixos como 343A, 343B
                    if (busLine.length === target.length + 1 && busLine.startsWith(target)) {
                        var suffix = busLine.charAt(busLine.length - 1);
                        return /[A-Za-z]/.test(suffix);
                    }

                    return false;
                });
            });

            // Resposta no formato que o App espera (STRINGS para lat/lng/velocidade)
            const responseData = filtered.map(function (bus) {
                return {
                    ordem: String(bus.ordem || ""),
                    linha: String(bus.linha || ""),
                    latitude: String(bus.latitude || ""),
                    longitude: String(bus.longitude || ""),
                    datahora: bus.datahora,
                    velocidade: String(bus.velocidade || "0")
                };
            });

            return new Response(JSON.stringify(responseData), {
                headers: Object.assign({}, corsHeaders, {
                    "X-Cache": cacheHit ? "HIT" : "MISS",
                    "X-Total": String(allBuses.length),
                    "X-Filtered": String(responseData.length)
                })
            });

        } catch (error) {
            return new Response(JSON.stringify({
                error: error.message,
                tip: "Tente novamente em alguns segundos"
            }), {
                status: 500,
                headers: corsHeaders
            });
        }
    }
};
