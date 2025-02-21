import { createRouter, createWebHashHistory } from "vue-router";
import Projects from "@/views/Projects.vue";
import ProjectsExplorer from "@/views/ProjectsExplorer.vue";
import Users from "@/views/Users.vue";
import Profiles from "@/views/Profiles.vue";
import Login from "@/views/Login.vue";

const routes = [
  {
    path: "/",
    redirect: "/projects",
  },
  {
    path: "/users",
    name: "users",
    component: Users,
  },
  {
    path: "/projects",
    name: "projects",
    component: Projects,
  },
  {
    path: "/login",
    name: "login",
    component: Login,
  },
  {
    path: "/projects-explorer/:projectId/:folderId?/:fileId?",
    name: "projects-explorer",
    component: ProjectsExplorer,
  },
  {
    path: "/profiles",
    name: "profiles",
    component: Profiles,
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

export default router;
