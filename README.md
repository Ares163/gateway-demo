# gateway-demo

Purpose:
  I want support non-standard status code and recording response body.
  
Problem:
  AbstractServerHttpResponse is not compatible with ServerHttpResponseDecorator。
  
How to invoke problem:
  start the project, access to http://localhost:8080/test
