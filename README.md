## Build and run

With JDK11+
```bash

gradle clean build runJar
```

## Exercise the application

```
curl -X POST -H "Content-Type: application/json" -d '{"array" : [0,1,2,3,4,5,6,7,8,9,10]}' http://localhost:8080/array

${array_id} is value returned in response from "/array" endpoint

curl -X GET -H "Content-Type: application/json" -v http://localhost:8080/array/${array_id}

curl -X GET -H "Content-Type: application/json" -v http://localhost:8080/array/${array_id}?async=true

```

## Try health and metrics

```
curl -s -X GET http://localhost:8080/health

curl -s -X GET http://localhost:8080/metrics
```
