// Keep one canvas video track throughout a recording; switch only its camera source.
export async function createRoundCamera(initialStream, deps = {}) {
  const media = deps.media || navigator.mediaDevices;
  const makeVideo = deps.makeVideo || (() => document.createElement('video'));
  const makeCanvas = deps.makeCanvas || (() => document.createElement('canvas'));
  const makeStream = deps.makeStream || (tracks => new MediaStream(tracks));
  const schedule = deps.schedule || (cb => setInterval(cb, 1000 / 24));
  const unschedule = deps.unschedule || clearInterval;
  const stopTracks = stream => stream?.getTracks().forEach(t => t.stop());
  let source = initialStream, sourceVideo = makeVideo(), closed = false, busy = false;
  let facing = source.getVideoTracks()[0]?.getSettings().facingMode || 'user';
  let canvasStream, timer;
  const audio = initialStream.getAudioTracks();
  const canvas = makeCanvas(); canvas.width = canvas.height = 480;
  const context = canvas.getContext('2d', { alpha: false });
  if (!context || typeof canvas.captureStream !== 'function') {
    stopTracks(initialStream);
    throw Error('Этот браузер не поддерживает переключение камеры при записи. Обнови браузер или загрузи готовый кружок.');
  }
  async function play(stream) {
    const video = makeVideo(); video.muted = true; video.autoplay = true; video.playsInline = true;
    video.srcObject = stream;
    try { await video.play(); } catch (error) { video.srcObject = null; throw error; }
    return video;
  }
  function draw() {
    if (closed || sourceVideo.readyState < 2 || !sourceVideo.videoWidth) return;
    const { videoWidth: w, videoHeight: h } = sourceVideo, side = Math.min(w, h);
    context.drawImage(sourceVideo, (w - side) / 2, (h - side) / 2, side, side, 0, 0, 480, 480);
  }
  function close() {
    if (closed) return; closed = true; unschedule(timer);
    stopTracks(source); audio.forEach(t => t.stop()); stopTracks(canvasStream);
    sourceVideo.pause(); sourceVideo.srcObject = null;
  }
  try {
    sourceVideo = await play(source); draw();
    canvasStream = canvas.captureStream(24);
    const stream = makeStream([...canvasStream.getVideoTracks(), ...audio]);
    timer = schedule(draw);
    return {
      stream, close,
      get facing() { return facing; },
      async flip() {
        if (closed || busy) return false;
        busy = true;
        const previous = facing, next = facing === 'user' ? 'environment' : 'user';
        // Release only camera tracks: some mobile devices cannot open both cameras at once.
        source.getVideoTracks().forEach(t => t.stop());
        async function open(mode, exact) {
          const fresh = await media.getUserMedia({ audio: false, video: {
            facingMode: exact ? { exact: mode } : { ideal: mode }, width: { ideal: 480 }, height: { ideal: 480 }
          }});
          if (closed) { stopTracks(fresh); return false; }
          let video;
          try { video = await play(fresh); } catch (e) { stopTracks(fresh); throw e; }
          if (closed) { video.pause(); video.srcObject = null; stopTracks(fresh); return false; }
          sourceVideo.pause(); sourceVideo.srcObject = null;
          source = fresh; sourceVideo = video; facing = fresh.getVideoTracks()[0]?.getSettings().facingMode || mode;
          draw(); return true;
        }
        try { return await open(next, true); }
        catch (error) {
          if (closed) return false;
          try { await open(previous, false); }
          catch { close(); throw Error('Камера отключилась. Закончи запись и попробуй снова.'); }
          throw Error('Вторая камера недоступна. Продолжаем запись с прежней камеры.');
        } finally { busy = false; }
      }
    };
  } catch (error) { close(); throw error; }
}
