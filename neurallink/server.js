const express = require("express");
const cors = require("cors");

const app = express();
app.use(cors());
app.use(express.json());

app.get("/hello", (req, res) => {
  res.json({ message: "Hello from backend!" });
});

app.post("/login", (req, res) => {
  const { username, password } = req.body;

  if (username === "admin" && password === "1234") {
    return res.json({ success: true, token: "abc123" });
  }

  res.status(401).json({ success: false });
});

app.listen(3000, () => {
  console.log("Server running on port 3000");
});