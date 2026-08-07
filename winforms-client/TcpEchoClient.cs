using System;
using System.IO;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace CommunicationClient
{
    internal sealed class TcpEchoClient : IDisposable
    {
        private readonly SemaphoreSlim sendLock = new SemaphoreSlim(1, 1);
        private TcpClient client;
        private StreamReader reader;
        private StreamWriter writer;

        public bool IsConnected => client != null && client.Connected;

        public async Task ConnectAsync(string host, int port, TimeSpan timeout)
        {
            Disconnect();
            var newClient = new TcpClient();
            client = newClient;
            var connectTask = newClient.ConnectAsync(host, port);
            var completed = await Task.WhenAny(connectTask, Task.Delay(timeout)).ConfigureAwait(false);
            if (completed != connectTask)
            {
                Disconnect();
                throw new TimeoutException("TCP 연결 시간이 초과되었습니다.");
            }

            try
            {
                await connectTask.ConfigureAwait(false);
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

            await sendLock.WaitAsync().ConfigureAwait(false);
            try
            {
                await writer.WriteLineAsync(message).ConfigureAwait(false);
                var readTask = reader.ReadLineAsync();
                var completed = await Task.WhenAny(readTask, Task.Delay(timeout)).ConfigureAwait(false);
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
