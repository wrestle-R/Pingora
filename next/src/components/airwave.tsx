export default function Airwave() {
  return (
    <div
      className="airwave"
      role="img"
      aria-label="A folded orange paper ribbon connects an illustrated Android phone and Linux laptop. Concept illustration, not a working app."
    >
      <div className="phone">
        <div className="camera" />
        <div className="phone-time">09:41</div>
        <span>Tuesday, a little closer.</span>
        <div className="phone-note">
          <b>Pingora</b>
          <br />
          Your desktop, within reach.
        </div>
      </div>
      <svg className="ribbon" viewBox="0 0 1200 400" aria-hidden="true">
        <defs>
          <linearGradient id="paper" x1="0" y1="0" x2="0.8" y2="1">
            <stop stopColor="#f39765" />
            <stop offset=".5" stopColor="#da502e" />
            <stop offset="1" stopColor="#a72f1c" />
          </linearGradient>
          <filter id="shadow">
            <feDropShadow dx="0" dy="15" stdDeviation="10" floodOpacity=".16" />
          </filter>
        </defs>
        <g filter="url(#shadow)">
          <path
            d="M40 160 210 80 410 290 660 165 830 235 1120 80 1155 210 850 355 655 285 400 390 185 190 65 300Z"
            fill="url(#paper)"
          />
          <path d="m210 80-25 110 225 100-10 100Z" fill="#f7bd93" />
          <path d="m660 165-5 120 175-50 20 120Z" fill="#f9b484" />
          <path d="m830 235 290-155-75 130-195 145Z" fill="#c53b23" />
          <path d="m40 160 170-80-25 110-120 110Z" fill="#ea6b40" />
        </g>
      </svg>
      <div className="laptop">
        <div className="laptop-screen">
          <span>pingora / your little connection</span>
          <div className="desktop-note">
            <i>↗</i>
            <div>
              <b>A little less back and forth.</b>
              <p>Phone + desktop, on the same page.</p>
            </div>
          </div>
        </div>
        <div className="laptop-base" />
      </div>
      <div className="floating-note">
        <span>↗</span>
        <div>
          <b>A file, on its way.</b>
          <small>Phone → desktop · illustration</small>
        </div>
      </div>
      <span className="art-caption">
        A glimpse of the idea. Apps are in development.
      </span>
    </div>
  );
}
