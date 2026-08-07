using System;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace CommunicationClient
{
    internal sealed class HttpEchoClient : IDisposable
    {
        private readonly HttpClient client = new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(5)
        };

        public async Task<HttpEchoResponse> PostAsync(string url, string message, CancellationToken cancellationToken)
        {
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

