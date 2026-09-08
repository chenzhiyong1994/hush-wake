"use strict";

const previewButtons = [...document.querySelectorAll("[data-preview]")];
const soundButtons = [...document.querySelectorAll("button[data-sound]")];
const sounds = {
  rain: { name: "绵密夜雨", description: "像窗外一场绵长的雨。", icon: "rain" },
  ocean: { name: "深夜海浪", description: "随着潮汐，把今天轻轻放下。", icon: "ocean" },
  forest: { name: "清晨林鸟", description: "在林间的第一声鸟鸣里，慢下来。", icon: "forest" },
  fire: { name: "轻柔壁炉", description: "听木柴轻响，留一室暖意。", icon: "fire" },
};

function showPreview(mode) {
  const isAlarm = mode === "alarm";
  document.querySelector("#alarm-preview").hidden = !isAlarm;
  document.querySelector("#sleep-preview").hidden = isAlarm;
  document.querySelector("#phone-alarm-nav").classList.toggle("active", isAlarm);
  document.querySelector("#phone-sleep-nav").classList.toggle("active", !isAlarm);
  previewButtons.forEach((button) => {
    button.setAttribute("aria-pressed", String(button.dataset.preview === mode));
  });
}

previewButtons.forEach((button) => {
  button.addEventListener("click", () => showPreview(button.dataset.preview));
});

soundButtons.forEach((button) => {
  button.addEventListener("click", () => {
    const key = button.dataset.sound;
    const sound = sounds[key];
    soundButtons.forEach((item) => item.setAttribute("aria-pressed", String(item === button)));
    document.querySelector("#sound-description").textContent = `${sound.name} · ${sound.description}`;
    document.querySelector("#preview-sound-name").textContent = sound.name;
    document.querySelector(".sleep-art").dataset.sound = key;
    document.querySelector(".sleep-art use").setAttribute("href", `#icon-${sound.icon}`);
    showPreview("sleep");
  });
  button.disabled = false;
});

document.querySelector(".preview-controls").hidden = false;
