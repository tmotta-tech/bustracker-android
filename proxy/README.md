# Soluções para API Lenta do Rio

## O Problema
A API `dados.mobilidade.rio/gps/sppo` retorna **214,000+ ônibus** (~30-50MB de JSON).
No 4G isso pode levar **20-30 minutos** para baixar e processar.

**Não existe outra API pública gratuita para ônibus do Rio.**

---

## Opções de Solução

### Opção 1: Cloudflare Worker (RECOMENDADA - Grátis)

Um proxy serverless que:
- Busca todos os ônibus UMA VEZ
- Faz cache por 30 segundos
- Retorna APENAS as linhas solicitadas (~1-50 KB vs 30 MB)

**Deploy:**
1. Crie conta em [dash.cloudflare.com](https://dash.cloudflare.com)
2. Vá em **Workers & Pages** → **Create Worker**
3. Cole o código de `proxy/cloudflare-worker.js`
4. Deploy e anote a URL (ex: `https://bus-proxy.seu-usuario.workers.dev`)
5. Atualize o app para usar essa URL

**Free tier:** 100,000 requests/dia (mais que suficiente)

---

### Opção 2: Usar Aplicativo Existente

Apps como **Moovit** ou **Google Maps** já têm dados de ônibus do Rio.
Se você só quer rastrear ônibus pessoalmente, esses apps funcionam bem.

---

### Opção 3: Hospedar Próprio Proxy (Vercel/Railway)

Similar ao Cloudflare Worker, mas em Node.js/Python.
Pode ser hospedado gratuitamente em Vercel, Railway, ou Render.

---

## Como Atualizar o App para Usar o Proxy

Em `BusRepository.kt`, mude a baseUrl:

```kotlin
private val rioApi = Retrofit.Builder()
    // ANTES: .baseUrl("https://dados.mobilidade.rio/")
    .baseUrl("https://SEU-WORKER.workers.dev/")  // ← Sua URL do proxy
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create(gson))
    .build()
    .create(BusService::class.java)
```

E em `BusService.kt`, ajuste o endpoint:

```kotlin
@GET("buses")  // ← O proxy usa /buses?linha=X
suspend fun getRioBuses(
    @Query("linha") linha: String? = null,
    @Query("_t") timestamp: Long = System.currentTimeMillis()
): Response<List<RioBusResponse>>
```

---

## Resultado Esperado

| Métrica | Antes (API direta) | Depois (com proxy) |
|---------|-------------------|-------------------|
| Dados transferidos | ~30 MB | ~10-50 KB |
| Tempo de resposta | 20-30 min | ~1-3 segundos |
| Buscas subsequentes | ~30 MB cada | ~10-50 KB cada |
