import { createApp } from "vue";
import {
    Alert,
    Button,
    Card,
    ConfigProvider,
    Empty,
    Layout,
    Pagination,
    Select,
    Skeleton,
    Tag
} from "ant-design-vue";
import App from "./App.vue";
import router from "./router";
import "ant-design-vue/dist/reset.css";
import "./styles.css";

const app = createApp(App);

[
    Alert,
    Button,
    Card,
    ConfigProvider,
    Empty,
    Layout,
    Pagination,
    Select,
    Skeleton,
    Tag
].forEach((component) => app.use(component));

app.use(router).mount("#app");
