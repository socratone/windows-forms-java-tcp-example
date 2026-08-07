using System;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace CommunicationClient
{
    // UTF-8 일반 텍스트를 POST하고 응답 정보를 화면용 객체로 변환한다.
    internal sealed class HttpEchoClient : IDisposable
    {
        // 연결 재사용을 위해 요청마다 만들지 않고 클라이언트 수명 동안 한 인스턴스를 유지한다.
        private readonly HttpClient client = new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(5)
        };

        public async Task<HttpEchoResponse> PostAsync(string url, string message, CancellationToken cancellationToken)
        {
            // using 범위가 끝나면 요청 본문과 응답 객체를 즉시 해제한다.
            using (var content = new StringContent(message, Encoding.UTF8, "text/plain"))
            using (var response = await client.PostAsync(url, content, cancellationToken).ConfigureAwait(false))
            {
                var responseText = await response.Content.ReadAsStringAsync().ConfigureAwait(false);
                return new HttpEchoResponse((int)response.StatusCode, response.ReasonPhrase, responseText);
            }
        }

        public void Dispose()
        {
            client.Dispose();
        }
    }

    internal sealed class HttpEchoResponse
    {
        // UI가 HttpResponseMessage의 수명과 무관하게 결과를 사용할 수 있도록 필요한 값만 보관한다.
        public HttpEchoResponse(int statusCode, string reasonPhrase, string body)
        {
            StatusCode = statusCode;
            ReasonPhrase = reasonPhrase;
            Body = body;
        }

        public int StatusCode { get; }
        public string ReasonPhrase { get; }
        public string Body { get; }
        public bool IsSuccessStatusCode => StatusCode >= 200 && StatusCode <= 299;
    }
}
