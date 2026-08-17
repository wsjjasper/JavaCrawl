document.addEventListener('DOMContentLoaded', () => {
    const slides = document.querySelectorAll('.slide');
    const progressBar = document.getElementById('progress-bar');
    const progressText = document.getElementById('progress-text');
    const container = document.querySelector('.presentation-container');
    const totalSlides = slides.length;

    const observerOptions = {
        root: container,
        rootMargin: '0px',
        threshold: 0.5
    };

    const observerCallback = (entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('active');

                // Update progress bar
                const index = Array.from(slides).indexOf(entry.target);
                const progress = (index / (totalSlides - 1)) * 100;
                progressBar.style.width = `${progress}%`;

                // Update progress text
                const day = entry.target.getAttribute('data-day');
                const label = entry.target.getAttribute('data-label');
                
                if (day && day !== '0' && label) {
                    progressText.textContent = `DAY ${day} · ${label}`;
                    progressText.style.opacity = '1';
                } else {
                    progressText.style.opacity = '0';
                }
            } else {
                // Remove active so animations replay on re-entry
                entry.target.classList.remove('active');
            }
        });
    };

    const observer = new IntersectionObserver(observerCallback, observerOptions);
    slides.forEach(slide => observer.observe(slide));

    // Music Player Logic
    const bgMusic = document.getElementById('bg-music');
    const musicBtn = document.getElementById('music-btn');
    const iconPlay = document.getElementById('music-icon-play');
    const iconPause = document.getElementById('music-icon-pause');

    if (musicBtn && bgMusic) {
        musicBtn.addEventListener('click', () => {
            if (bgMusic.paused) {
                bgMusic.play().then(() => {
                    iconPlay.style.display = 'none';
                    iconPause.style.display = 'block';
                    musicBtn.classList.add('playing');
                }).catch(e => {
                    console.log("Audio play failed:", e);
                });
            } else {
                bgMusic.pause();
                iconPlay.style.display = 'block';
                iconPause.style.display = 'none';
                musicBtn.classList.remove('playing');
            }
        });
    }
});
