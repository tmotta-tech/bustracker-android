# API Optimization Guide

Apply the following changes to your `mobilidade-rio-api` repository to enable efficient server-side filtering. This will allow the Android app to request specific bus lines (e.g., `?linha=100`) and receive a small, filtered response instead of the entire fleet.

## 1. `mobilidade_rio/dados/views.py`

Update `SppoViewSet` to extract `linha` from query parameters and pass it to the utils class.

```python
class SppoViewSet(viewsets.ViewSet):
    """
    Return data from SPPO with extra columns.
    
    Parameters:
    - **linha**: optional (filter by line number)
    """

    logger = logging.getLogger('DadosGpsSppoViewSet')

    def list(self, _):
        """GET"""
        start = dt.now()

        params = self.request.query_params
        utils = DadosGpsSppoUtils()
        error: DadosUtilsFailedException = None
        result_list: List[Dict[str, any]] = []

        # [NEW] Get 'linha' parameter
        linha_filter = params.get("linha", None)

        try:
            result = utils.run(
                data_inicial=params.get("data_inicial", ""),
                data_final=params.get("data_final", ""),
                linha=linha_filter  # [NEW] Pass to run method
            )
            result_list = result.to_dict(orient='records')
        except DadosUtilsFailedException as exception:
            error = exception

        # ... (rest of the file remains the same)
```

## 2. `mobilidade_rio/dados/utils.py`

Update `DadosGpsSppoUtils` to accept `linha` and pass it to the upstream API (or filter locally if upstream doesn't support it).

```python
class DadosGpsSppoUtils:
    # ...

    # [MODIFIED] Add linha parameter default None
    def run(self, data_inicial='', data_final='', linha=None):
        """Executar tarefa completa"""
        self._validate_params(data_inicial, data_final)
        
        # [MODIFIED] Pass linha to _get_sppo_api
        sppo_api = self._get_sppo_api(data_inicial, data_final, linha)
        
        # Optimization: BigQuery only processes vehicles returned by the API
        id_veiculos = list(pd.unique(sppo_api["ordem"].values.ravel()))
        
        if not id_veiculos:
             return sppo_api # Return empty if no buses found
             
        sppo_bq = self._get_sppo_bigquery(id_veiculos)
        sppo_join = self._join_sppo_api_bigquery(sppo_api, sppo_bq)
        return sppo_join

    # [MODIFIED] Add linha parameter
    def _get_sppo_api(self, data_inicial='', data_final='', linha=None):
        """
        Obter dados da API SPPO
        """
        start = dt.now()
        url = "https://dados.mobilidade.rio/gps/sppo"

        # Get params
        params = {'dataInicial': data_inicial, 'dataFinal': data_final}
        
        # [NEW] Add linha to params if present
        if linha:
            params['linha'] = linha
            
        if not params["dataInicial"]:
            del params["dataInicial"]
        if not params["dataFinal"]:
             del params["dataFinal"]
        if not params:
            params = None

        # Run
        try:
            response = requests.get(
                url, timeout=self.request_timeout, params=params)
             
            # ... (error handling code remains the same) ...

        # Treating response data
        # If the upstream 'linha' filter works, 'data' will already be filtered.
        # If upstream ignores it, we can filter here as a fallback using pandas:
        
        data = pd.DataFrame(pd.json_normalize(response.json()))
        
        # [NEW] Fallback client-side filtering (if upstream didn't filter)
        if linha and not data.empty and 'linha' in data.columns:
             # Basic filter, can be improved with regex if needed
             # normalizing to string just in case
             data = data[data['linha'].astype(str).str.contains(linha, na=False)]

        # Log
        elapsed_time = round((dt.now() - start).total_seconds(), 2)
        logger.info("Requisição para o SPPO durou %ss", elapsed_time)

        return data
```
