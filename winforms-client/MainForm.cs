using System;
using System.Drawing;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace CommunicationClient
{
    // TCP와 HTTP 에코 서버를 각각 시험할 수 있는 메인 화면이다.
    public sealed class MainForm : Form
    {
        // 연결 또는 응답이 무한정 지연되지 않도록 네트워크 작업의 최대 대기 시간을 제한한다.
        private static readonly TimeSpan NetworkTimeout = TimeSpan.FromSeconds(5);
        private readonly TcpEchoClient tcpClient = new TcpEchoClient();
        private readonly HttpEchoClient httpClient = new HttpEchoClient();
        private readonly CancellationTokenSource closing = new CancellationTokenSource();

        private readonly TextBox tcpHost = new TextBox { Text = "127.0.0.1" };
        private readonly NumericUpDown tcpPort = new NumericUpDown { Minimum = 1, Maximum = 65535, Value = 5000 };
        private readonly Button tcpConnect = new Button { Text = "연결" };
        private readonly Button tcpDisconnect = new Button { Text = "연결 해제", Enabled = false };
        private readonly TextBox tcpMessage = new TextBox();
        private readonly Button tcpSend = new Button { Text = "전송", Enabled = false };
        private readonly TextBox tcpLog = CreateLogBox();

        private readonly TextBox httpUrl = new TextBox { Text = "http://127.0.0.1:8080/echo" };
        private readonly TextBox httpMessage = new TextBox();
        private readonly Button httpSend = new Button { Text = "POST 전송" };
        private readonly TextBox httpLog = CreateLogBox();

        public MainForm()
        {
            // 디자이너 파일 없이 코드만으로 폼의 기본 속성과 탭 UI를 구성한다.
            Text = "WinForms - Java TCP/HTTP 통신 예제";
            ClientSize = new Size(840, 560);
            MinimumSize = new Size(720, 480);
            StartPosition = FormStartPosition.CenterScreen;

            var tabs = new TabControl { Dock = DockStyle.Fill };
            tabs.TabPages.Add(BuildTcpPage());
            tabs.TabPages.Add(BuildHttpPage());
            Controls.Add(tabs);

            // 각 UI 이벤트를 실제 네트워크 작업 메서드에 연결한다.
            tcpConnect.Click += async (_, __) => await ConnectTcpAsync();
            tcpDisconnect.Click += (_, __) => DisconnectTcp("사용자가 연결을 해제했습니다.");
            tcpSend.Click += async (_, __) => await SendTcpAsync();
            httpSend.Click += async (_, __) => await SendHttpAsync();
            tcpMessage.KeyDown += async (_, e) => await SendOnEnterAsync(e, SendTcpAsync);
            httpMessage.KeyDown += async (_, e) => await SendOnEnterAsync(e, SendHttpAsync);
            FormClosing += OnFormClosing;
        }

        private TabPage BuildTcpPage()
        {
            // 연결 설정, 메시지 입력, 통신 기록을 세로로 배치한다.
            var page = new TabPage("TCP 통신") { Padding = new Padding(12) };
            var root = NewTable(5);
            root.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            root.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            root.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            root.RowStyles.Add(new RowStyle(SizeType.AutoSize));

            var connection = new FlowLayoutPanel { Dock = DockStyle.Fill, AutoSize = true, WrapContents = false };
            connection.Controls.Add(new Label { Text = "호스트", AutoSize = true, Margin = new Padding(3, 8, 3, 3) });
            tcpHost.Width = 170;
            connection.Controls.Add(tcpHost);
            connection.Controls.Add(new Label { Text = "포트", AutoSize = true, Margin = new Padding(12, 8, 3, 3) });
            tcpPort.Width = 90;
            connection.Controls.Add(tcpPort);
            connection.Controls.Add(tcpConnect);
            connection.Controls.Add(tcpDisconnect);
            root.Controls.Add(connection, 0, 0);

            root.Controls.Add(new Label { Text = "메시지 (한 줄)", AutoSize = true, Margin = new Padding(3, 10, 3, 3) }, 0, 1);
            root.Controls.Add(BuildMessageRow(tcpMessage, tcpSend), 0, 2);
            root.Controls.Add(tcpLog, 0, 3);
            root.Controls.Add(new Label { Text = "TCP는 연결 후 여러 메시지를 연속해서 전송합니다.", AutoSize = true }, 0, 4);
            page.Controls.Add(root);
            return page;
        }

        private TabPage BuildHttpPage()
        {
            // HTTP는 연결 과정 없이 URL과 본문을 입력해 요청하도록 구성한다.
            var page = new TabPage("HTTP 통신") { Padding = new Padding(12) };
            var root = NewTable(6);
            root.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            root.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            root.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            root.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            root.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            root.Controls.Add(new Label { Text = "요청 URL", AutoSize = true }, 0, 0);
            httpUrl.Dock = DockStyle.Fill;
            root.Controls.Add(httpUrl, 0, 1);
            root.Controls.Add(new Label { Text = "메시지", AutoSize = true, Margin = new Padding(3, 10, 3, 3) }, 0, 2);
            root.Controls.Add(BuildMessageRow(httpMessage, httpSend), 0, 3);
            root.Controls.Add(httpLog, 0, 4);
            root.Controls.Add(new Label { Text = "UTF-8 text/plain 본문을 POST /echo로 전송합니다.", AutoSize = true }, 0, 5);
            page.Controls.Add(root);
            return page;
        }

        private static TableLayoutPanel NewTable(int rows)
        {
            // 한 개 열이 가용 너비를 모두 차지하는 공통 레이아웃을 만든다.
            var table = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 1, RowCount = rows };
            table.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            return table;
        }

        private static Control BuildMessageRow(TextBox message, Button send)
        {
            // 입력란은 남은 너비를 사용하고 전송 버튼은 필요한 크기만 차지한다.
            var row = new TableLayoutPanel { Dock = DockStyle.Fill, AutoSize = true, ColumnCount = 2 };
            row.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            row.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
            message.Dock = DockStyle.Fill;
            send.AutoSize = true;
            row.Controls.Add(message, 0, 0);
            row.Controls.Add(send, 1, 0);
            return row;
        }

        private static TextBox CreateLogBox()
        {
            // 사용자가 수정할 수 없는 여러 줄 통신 기록 창을 만든다.
            return new TextBox
            {
                Dock = DockStyle.Fill,
                Multiline = true,
                ReadOnly = true,
                ScrollBars = ScrollBars.Vertical,
                BackColor = SystemColors.Window,
                Font = new Font("Consolas", 10f)
            };
        }

        private async Task ConnectTcpAsync()
        {
            // 중복 클릭을 막은 상태에서 입력한 주소로 TCP 연결을 시도한다.
            SetTcpBusy(true);
            try
            {
                Log(tcpLog, $"연결 시도: {tcpHost.Text}:{tcpPort.Value}");
                await tcpClient.ConnectAsync(tcpHost.Text.Trim(), (int)tcpPort.Value, NetworkTimeout);
                Log(tcpLog, "연결 성공");
            }
            catch (Exception ex)
            {
                if (!closing.IsCancellationRequested) Log(tcpLog, "연결 실패: " + ex.Message);
                tcpClient.Disconnect();
            }
            finally
            {
                if (!closing.IsCancellationRequested) SetTcpBusy(false);
            }
        }

        private async Task SendTcpAsync()
        {
            var message = tcpMessage.Text;
            if (string.IsNullOrEmpty(message))
            {
                Log(tcpLog, "전송할 메시지를 입력해 주세요.");
                return;
            }

            // 하나의 요청과 응답이 끝날 때까지 추가 전송을 막는다.
            tcpSend.Enabled = false;
            try
            {
                Log(tcpLog, "보냄 > " + message);
                var response = await tcpClient.SendAsync(message, NetworkTimeout);
                Log(tcpLog, "받음 < " + response);
                tcpMessage.SelectAll();
                tcpMessage.Focus();
            }
            catch (Exception ex)
            {
                if (!closing.IsCancellationRequested) Log(tcpLog, "TCP 오류: " + ex.Message);
            }
            finally
            {
                if (!closing.IsCancellationRequested) SetTcpBusy(false);
            }
        }

        private async Task SendHttpAsync()
        {
            if (string.IsNullOrWhiteSpace(httpUrl.Text) || string.IsNullOrEmpty(httpMessage.Text))
            {
                Log(httpLog, "URL과 메시지를 입력해 주세요.");
                return;
            }

            // 폼이 닫히면 closing.Token이 진행 중인 HTTP 요청도 함께 취소한다.
            httpSend.Enabled = false;
            try
            {
                Log(httpLog, "POST > " + httpMessage.Text);
                var response = await httpClient.PostAsync(httpUrl.Text.Trim(), httpMessage.Text, closing.Token);
                Log(httpLog, $"HTTP {response.StatusCode} {response.ReasonPhrase} < {response.Body}");
                if (!response.IsSuccessStatusCode) Log(httpLog, "서버가 오류 상태 코드를 반환했습니다.");
                httpMessage.SelectAll();
                httpMessage.Focus();
            }
            catch (OperationCanceledException) when (!closing.IsCancellationRequested)
            {
                Log(httpLog, "HTTP 요청 시간이 초과되었습니다.");
            }
            catch (Exception ex)
            {
                if (!closing.IsCancellationRequested) Log(httpLog, "HTTP 오류: " + ex.Message);
            }
            finally
            {
                if (!closing.IsCancellationRequested) httpSend.Enabled = true;
            }
        }

        private void SetTcpBusy(bool busy)
        {
            // 작업 진행 여부와 실제 연결 상태에 맞춰 조작 가능한 컨트롤만 활성화한다.
            var connected = tcpClient.IsConnected;
            tcpConnect.Enabled = !busy && !connected;
            tcpDisconnect.Enabled = !busy && connected;
            tcpSend.Enabled = !busy && connected;
            tcpHost.Enabled = !busy && !connected;
            tcpPort.Enabled = !busy && !connected;
        }

        private void DisconnectTcp(string message)
        {
            // 소켓 자원을 정리한 뒤 화면을 미연결 상태로 되돌린다.
            tcpClient.Disconnect();
            Log(tcpLog, message);
            SetTcpBusy(false);
        }

        private static async Task SendOnEnterAsync(KeyEventArgs e, Func<Task> sender)
        {
            // 입력란에서 Enter를 누르면 줄바꿈 대신 해당 프로토콜의 전송을 실행한다.
            if (e.KeyCode != Keys.Enter) return;
            e.SuppressKeyPress = true;
            await sender();
        }

        private static void Log(TextBox box, string message)
        {
            // 송수신 순서를 알아볼 수 있도록 현재 시각과 함께 기록한다.
            box.AppendText($"[{DateTime.Now:HH:mm:ss}] {message}{Environment.NewLine}");
        }

        private void OnFormClosing(object sender, FormClosingEventArgs e)
        {
            // 종료 중인 비동기 작업을 취소하고 모든 네트워크 자원을 해제한다.
            closing.Cancel();
            tcpClient.Dispose();
            httpClient.Dispose();
        }
    }
}
