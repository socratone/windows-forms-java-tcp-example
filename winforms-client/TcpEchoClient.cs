using System;
using System.IO;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace CommunicationClient
{
    // 줄바꿈을 메시지 경계로 사용하는 지속형 TCP 에코 클라이언트다.
    internal sealed class TcpEchoClient : IDisposable
    {
        // 동일한 스트림에서 여러 요청과 응답이 섞이지 않도록 전송을 한 번에 하나로 제한한다.
        private readonly SemaphoreSlim sendLock = new SemaphoreSlim(1, 1);
        private TcpClient client;
        private StreamReader reader;
        private StreamWriter writer;

        public bool IsConnected => client != null && client.Connected;

        public async Task ConnectAsync(string host, int port, TimeSpan timeout)
        {
            // 기존 연결을 먼저 정리하여 한 인스턴스가 하나의 연결만 소유하게 한다.
            Disconnect();
            var newClient = new TcpClient();
            client = newClient;
            var connectTask = newClient.ConnectAsync(host, port);
            // 구형 .NET의 ConnectAsync에는 취소 토큰이 없으므로 지연 작업과 경쟁시켜 타임아웃을 구현한다.
            var completed = await Task.WhenAny(connectTask, Task.Delay(timeout)).ConfigureAwait(false);
            if (completed != connectTask)
            {
                Disconnect();
                throw new TimeoutException("TCP 연결 시간이 초과되었습니다.");
            }

            try
            {
                await connectTask.ConfigureAwait(false);
                // 양쪽 프로그램이 동일하게 UTF-8 한 줄 단위로 읽고 쓰도록 스트림을 감싼다.
                var stream = newClient.GetStream();
                reader = new StreamReader(stream, new UTF8Encoding(false), false, 1024, true);
                writer = new StreamWriter(stream, new UTF8Encoding(false), 1024, true) { AutoFlush = true };
            }
            catch
            {
                Disconnect();
                throw;
            }
        }

        public async Task<string> SendAsync(string message, TimeSpan timeout)
        {
            if (!IsConnected || reader == null || writer == null)
                throw new InvalidOperationException("TCP 서버에 먼저 연결해 주세요.");
            if (message.IndexOfAny(new[] { '\r', '\n' }) >= 0)
                throw new ArgumentException("TCP 메시지에는 줄바꿈을 사용할 수 없습니다.", nameof(message));

            // WriteLine 한 번에 대해 ReadLine 한 번을 짝지어 에코 응답을 정확히 돌려받는다.
            await sendLock.WaitAsync().ConfigureAwait(false);
            try
            {
                await writer.WriteLineAsync(message).ConfigureAwait(false);
                var readTask = reader.ReadLineAsync();
                var completed = await Task.WhenAny(readTask, Task.Delay(timeout)).ConfigureAwait(false);
                // 응답 시간이 초과된 연결은 뒤늦은 응답이 다음 요청과 섞일 수 있으므로 폐기한다.
                if (completed != readTask)
                {
                    Disconnect();
                    throw new TimeoutException("TCP 응답 시간이 초과되어 연결을 종료했습니다.");
                }

                var response = await readTask.ConfigureAwait(false);
                if (response == null)
                {
                    Disconnect();
                    throw new IOException("서버가 TCP 연결을 종료했습니다.");
                }
                return response;
            }
            catch
            {
                Disconnect();
                throw;
            }
            finally
            {
                sendLock.Release();
            }
        }

        public void Disconnect()
        {
            // 래퍼와 소켓을 모두 닫고 필드를 비워 재연결 가능한 상태로 만든다.
            writer?.Dispose();
            reader?.Dispose();
            client?.Close();
            writer = null;
            reader = null;
            client = null;
        }

        public void Dispose()
        {
            Disconnect();
        }
    }
}
